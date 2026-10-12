#!/usr/bin/env python3
"""Report pinned versions in this repo next to the latest stable release of each.

Read-only: it never edits files. Run from the repository root:

    python3 .claude/skills/update-stack/scripts/check_versions.py

Sources: Maven Central, the Gradle Plugin Portal, services.gradle.org, the GitHub
releases API, and the Adoptium API. Network calls go through curl.
"""
import json
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

BUILD_FILES = ["build.gradle", "sample/build.gradle", "README.md"]

# Maven coordinates pinned as 'group:artifact:version' in BUILD_FILES.
MAVEN = [
    "org.aspectj:aspectjrt",
    "org.aspectj:aspectjtools",
    "io.prometheus:simpleclient",
    "io.prometheus:simpleclient_common",
    "org.slf4j:slf4j-api",
    "org.junit:junit-bom",
    "org.mockito:mockito-core",
]

# Gradle plugins pinned as id '<id>' version '<version>' in BUILD_FILES.
PLUGINS = [
    "org.springframework.boot",
    "io.freefair.aspectj.post-compile-weaving",
]

# Releases that will never get a newer version under the same coordinates.
FINAL = {
    "io.prometheus:simpleclient": "final 0.x release; successor is io.prometheus:prometheus-metrics-* (different API)",
    "io.prometheus:simpleclient_common": "final 0.x release; successor is io.prometheus:prometheus-metrics-* (different API)",
}

UNSTABLE = re.compile(r"(alpha|beta|rc|cr|-m\d|\.m\d|milestone|snapshot|preview|ea\b|-dev)", re.I)


def fetch(url):
    result = subprocess.run(
        ["curl", "-sSfL", "--max-time", "30", "-H", "User-Agent: update-stack-skill", url],
        capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(f"{url}: {result.stderr.strip()}")
    return result.stdout


def version_key(version):
    return [int(p) if p.isdigit() else p for p in re.split(r"[.\-_+]", version)]


def latest_stable(versions):
    stable = [v for v in versions if not UNSTABLE.search(v)]
    return max(stable, key=version_key) if stable else None


def maven_latest(base, path):
    root = ET.fromstring(fetch(f"{base}/{path}/maven-metadata.xml"))
    return latest_stable([v.text for v in root.iter("version")])


def find(pattern):
    """Yield (file, line number, version) for every match of pattern in BUILD_FILES."""
    for name in BUILD_FILES:
        path = Path(name)
        if not path.exists():
            continue
        for number, line in enumerate(path.read_text().splitlines(), 1):
            for match in re.finditer(pattern, line):
                yield f"{name}:{number}", match.group(1)


rows = []  # (component, location, current, latest, note, outdated)


def add(component, location, current, latest, note="", compare=None, outdated=None):
    """compare: the value current must equal to count as up to date (defaults to latest)."""
    if outdated is None:
        target = compare if compare is not None else latest
        outdated = target is not None and current != target
    rows.append((component, location, current, latest or "?", note, outdated))


def check_maven():
    for coordinate in MAVEN:
        group, artifact = coordinate.split(":")
        try:
            latest = maven_latest("https://repo1.maven.org/maven2", f"{group.replace('.', '/')}/{artifact}")
        except RuntimeError as error:
            latest, FINAL[coordinate] = None, str(error)
        for location, current in find(re.escape(coordinate) + r":([0-9][^'\")\s]*)"):
            add(coordinate, location, current, latest, FINAL.get(coordinate, ""))


def check_plugins():
    for plugin in PLUGINS:
        path = f"{plugin.replace('.', '/')}/{plugin}.gradle.plugin"
        try:
            latest = maven_latest("https://plugins.gradle.org/m2", path)
        except RuntimeError as error:
            latest, note = None, str(error)
        else:
            note = ""
        for location, current in find(r"id ['\"]" + re.escape(plugin) + r"['\"] version ['\"]([^'\"]+)"):
            add(plugin, location, current, latest, note)


def check_gradle():
    props = Path("gradle/wrapper/gradle-wrapper.properties").read_text()
    current = re.search(r"gradle-([0-9][^-]*)-(?:bin|all)\.zip", props).group(1)
    latest = json.loads(fetch("https://services.gradle.org/versions/current"))["version"]
    add("Gradle wrapper", "gradle/wrapper/gradle-wrapper.properties", current, latest)


def check_actions():
    workflow = Path(".github/workflows/ci.yml")
    seen = {}
    for number, line in enumerate(workflow.read_text().splitlines(), 1):
        match = re.search(r"uses:\s*([\w.-]+/[\w.-]+)(?:/[\w./-]+)?@(\S+)", line)
        if not match:
            continue
        repo, current = match.groups()
        if repo not in seen:
            try:
                tag = json.loads(fetch(f"https://api.github.com/repos/{repo}/releases/latest"))["tag_name"]
                seen[repo] = (re.match(r"v?\d+", tag).group(0), tag)
            except (RuntimeError, KeyError, AttributeError):
                seen[repo] = (None, None)
        major, tag = seen[repo]
        add(repo, f"{workflow}:{number}", current, f"{major} ({tag})" if major else None, compare=major)


def check_java():
    info = json.loads(fetch("https://api.adoptium.net/v3/info/available_releases"))
    lts = str(info["most_recent_lts"])
    latest = f"LTS {lts} (feature {info['most_recent_feature_release']})"
    note = "newer LTS available; moving is a runtime decision, ask first"
    found = list(find(r"JavaLanguageVersion\.of\((\d+)\)"))
    for name in ["Dockerfile", ".github/workflows/ci.yml"]:
        for number, line in enumerate(Path(name).read_text().splitlines(), 1):
            for match in re.finditer(r"eclipse-temurin:(\S+)|java-version:\s*'?(\d+)", line):
                found.append((f"{name}:{number}", match.group(1) or match.group(2)))
    for location, current in found:
        major = re.match(r"\d+", current).group(0)
        add("Java", location, current, latest, note if major != lts else "", outdated=major != lts)


def main():
    if not Path("settings.gradle").exists():
        sys.exit("Run from the repository root.")
    for check in (check_gradle, check_maven, check_plugins, check_actions, check_java):
        try:
            check()
        except Exception as error:  # keep reporting the rest
            add(check.__name__, "-", "-", None, f"lookup failed: {error}")

    header = ("", "Component", "Location", "Current", "Latest")
    table = [("*" if outdated else " ", c, loc, cur, lat) for c, loc, cur, lat, _, outdated in rows]
    widths = [max(len(r[i]) for r in table + [header]) for i in range(5)]
    print("  ".join(h.ljust(w) for h, w in zip(header, widths)) + "  Note")
    for cells, row in zip(table, rows):
        print("  ".join(v.ljust(w) for v, w in zip(cells, widths)) + (f"  {row[4]}" if row[4] else ""))
    outdated = sum(1 for row in rows if row[5])
    print(f"\n{outdated} pinned version(s) marked * differ from the latest stable release.")


if __name__ == "__main__":
    main()

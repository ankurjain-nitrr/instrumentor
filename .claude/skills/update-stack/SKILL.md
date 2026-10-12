---
name: update-stack
description: Update every pinned version in the instrumentor repo to its latest stable release (Gradle wrapper, Maven dependencies, Gradle plugins, Spring Boot, GitHub Actions, Docker base images, Java) and verify the build, tests, weaving and Docker image still work. Use when asked to "update the stack", "bump dependencies", "upgrade versions" or "check for outdated versions".
---

# Update the stack

Bring every pinned version in this repo up to its latest stable release without changing
behavior: metric names, labels and `@TrackMetrics` defaults stay exactly as they are.

## 1. Find what is outdated

Read the current versions from the files in section 2, then look up the latest stable release
of each. Stable means no alpha, beta, RC, milestone, snapshot or preview builds; metadata lists
are not always sorted, so compare versions rather than taking the last entry.

| What | Where to look |
|---|---|
| Maven dependencies | `https://repo1.maven.org/maven2/<group path>/<artifact>/maven-metadata.xml` (e.g. `org/aspectj/aspectjrt`) |
| Gradle plugins | `https://plugins.gradle.org/m2/<id path>/<id>.gradle.plugin/maven-metadata.xml` (e.g. `org/springframework/boot/org.springframework.boot.gradle.plugin`) |
| Gradle | `https://services.gradle.org/versions/current` (version, distribution and wrapper checksums) |
| GitHub Actions | `https://api.github.com/repos/<owner>/<repo>/releases/latest` |
| Java and Temurin images | `https://api.adoptium.net/v3/info/available_releases` (`most_recent_lts`) and Docker Hub tags for `eclipse-temurin` |

Tell the user what will change, old and new version for each, before editing.

## 2. Where versions live

Keep each version identical everywhere it appears. The table lists the known places, not every
version: also scan `build.gradle`, `sample/build.gradle`, `ci.yml`, `Dockerfile` and `README.md` for
any pinned version it doesn't name, and after editing grep the repo for each old version to catch
copies you missed.

| What | Where |
|---|---|
| Gradle | `gradle/wrapper/gradle-wrapper.properties`, `gradle-wrapper.jar`, `gradlew`, `gradlew.bat` |
| Library dependencies | `build.gradle` (mockito-core appears twice: dependency and `mockitoAgent`) |
| Sample plugins and dependencies | `sample/build.gradle` |
| Consumer snippet | `README.md` Setup section (must match the library's and sample's versions) |
| GitHub Actions | `.github/workflows/ci.yml` (major tags such as `@vN`) |
| Java | toolchains in both build files, the ajc `-<java>` release flag in `build.gradle`, `ci.yml` `java-version` and step name, `Dockerfile` base images and comment, `scripts/docker-smoke-test.sh`, README |

Dependencies the Spring Boot BOM manages (Spring, Tomcat, Jackson, the sample's JUnit and
Mockito) move with the Spring Boot version; don't pin them.

## 3. Rules

- **Prometheus client:** the `io.prometheus:simpleclient` artifacts are end-of-life; take their
  latest 0.x release but don't leave that line. Their successor, `io.prometheus:prometheus-metrics-*` 1.x, has a different API (no
  `CollectorRegistry`), so moving to it changes behavior for consumers. Don't do it unless the user
  asks for that migration explicitly.
- **AspectJ:** keep `aspectjrt` and `aspectjtools` on the same version. The library build runs
  `ajc` from the `ajc` configuration after `compileJava`; without it the published aspect has no
  `aspectOf()`, and consumers weaving with `-aspectpath` fail with `NoSuchMethodError`.
- **Mockito:** the `mockitoAgent` version must match `mockito-core`.
- **Gradle wrapper:** regenerate it instead of editing the properties file by hand, and pin the
  distribution checksum from `https://services.gradle.org/versions/current`:

  ```sh
  ./gradlew wrapper --gradle-version <v> --distribution-type bin --gradle-distribution-sha256-sum <sha>
  ./gradlew wrapper --gradle-version <v> --distribution-type bin --gradle-distribution-sha256-sum <sha>
  ```

  Run it twice: the second run uses the new Gradle to refresh `gradle-wrapper.jar` and the scripts.
  Check the jar against `wrapperChecksum` from the same endpoint. If Java can't download the
  distribution but curl can, download the zip with curl, verify its SHA-256, run that Gradle's
  `bin/gradle wrapper ... --no-validate-url`, then set `validateDistributionUrl=true` again.
- **GitHub Actions:** use the major tag of the latest release (`v7.0.1` → `@v7`), and confirm it exists with
  `git ls-remote --tags https://github.com/<owner>/<repo> refs/tags/<tag>`.
- **Major versions:** read the release notes for breaking changes (new minimum Java, renamed
  modules or packages, removed APIs), especially for Gradle, Spring Boot, JUnit and freefair.

## 4. Verify

Run all of these on the JDK the project targets; each must pass before committing. If that JDK
isn't installed, download Temurin for it from
`https://api.adoptium.net/v3/assets/latest/<java>/hotspot?os=<os>&architecture=<arch>&image_type=jdk`
into a scratch directory, check the archive against `binary.package.checksum` (parse the JSON; it
holds several checksums), and run Gradle with `JAVA_HOME` set to it and
`-Porg.gradle.java.installations.paths=$JAVA_HOME`.

```sh
./gradlew clean build                          # library tests + sample tests

# Classes target the toolchain's Java version (major version = Java version + 44) and the
# aspect was woven by ajc.
javap -v -cp build/classes/java/main com.ankur.instrumentor.AppMetricsAdvice | grep -E 'major version|aspectOf\(\)'

docker build -t instrumentor-sample .
scripts/docker-smoke-test.sh instrumentor-sample
```

The sample's `OrderServiceWeavingTest` and the smoke test fail if the advice stops being woven
or stops recording metrics, so a green run shows metric capture still works end to end.

## 5. Commit

- Use small commits, one area each: wrapper, library dependencies, sample (Spring Boot and
  freefair), CI actions, Docker images, README.
- Don't push unless the user asks.
- Finish with a table of old and new versions, anything skipped and why, and the verification
  results.

---
name: update-stack
description: Instrumentor specifics for updating every pinned version (Gradle wrapper, Maven dependencies, Gradle plugins, Spring Boot, GitHub Actions, Docker base images, Java) on top of the shared update-stack skill. Use when asked to "update the stack", "bump dependencies", "upgrade versions" or "check for outdated versions".
---

# Update the stack: instrumentor

Follow the shared `update-stack` skill (`update-stack@ankurjain-skills`, enabled in
`.claude/settings.json`) for the process. This file adds only what is specific to this repo and wins
where the two differ.

Behavior that must not change: metric names, labels and `@TrackMetrics` defaults.

## Where to look up versions

| What | Source |
|---|---|
| Maven dependencies | `https://repo1.maven.org/maven2/<group path>/<artifact>/maven-metadata.xml` |
| Gradle plugins | `https://plugins.gradle.org/m2/<id path>/<id>.gradle.plugin/maven-metadata.xml` |
| Gradle | `https://services.gradle.org/versions/current` (version, distribution and wrapper checksums) |
| Java and Temurin images | `https://api.adoptium.net/v3/info/available_releases` (`most_recent_lts`), Docker Hub tags for `eclipse-temurin` |

## Where versions live

| What | Where |
|---|---|
| Gradle | `gradle/wrapper/gradle-wrapper.properties`, `gradle-wrapper.jar`, `gradlew`, `gradlew.bat` |
| Library dependencies | `build.gradle` (mockito-core appears twice: dependency and `mockitoAgent`) |
| Sample plugins and dependencies | `sample/build.gradle` |
| Consumer snippet | `README.md` Setup section (must match the library's and sample's versions) |
| GitHub Actions | `.github/workflows/ci.yml` |
| Java | toolchains in both build files, the ajc `-<java>` release flag in `build.gradle`, `ci.yml` `java-version` and step name, `Dockerfile` base images and comment, `scripts/docker-smoke-test.sh`, README |

The Spring Boot BOM manages Spring, Tomcat, Jackson and the sample's JUnit and Mockito; don't pin them.

## Rules

- **Prometheus client:** `io.prometheus:simpleclient*` is end-of-life; stay on its latest 0.x. The
  successor `io.prometheus:prometheus-metrics-*` has a different API (no `CollectorRegistry`), so
  moving to it changes behavior for consumers. Only on explicit request.
- **AspectJ:** `aspectjrt` and `aspectjtools` stay on the same version. The library build runs `ajc`
  from the `ajc` configuration after `compileJava`; without it the published aspect has no
  `aspectOf()` and consumers weaving with `-aspectpath` fail with `NoSuchMethodError`.
- **Mockito:** the `mockitoAgent` version matches `mockito-core`.
- **Java:** raising it raises consumers' minimum runtime; say so in the report.
- **Major versions to read release notes for:** Gradle, Spring Boot, JUnit, freefair.
- **Gradle wrapper:** run twice (the second run uses the new Gradle to refresh the jar and scripts),
  then check `gradle-wrapper.jar` against `wrapperChecksum`:

  ```sh
  ./gradlew wrapper --gradle-version <v> --distribution-type bin --gradle-distribution-sha256-sum <sha>
  ```

  If Java can't download the distribution but curl can, download the zip with curl, verify its
  SHA-256, run that Gradle's `bin/gradle wrapper ... --no-validate-url`, then set
  `validateDistributionUrl=true` again.
- **Missing JDK:** get Temurin from
  `https://api.adoptium.net/v3/assets/latest/<java>/hotspot?os=<os>&architecture=<arch>&image_type=jdk`
  (checksum: `binary.package.checksum`) and run Gradle with `JAVA_HOME` set to it and
  `-Porg.gradle.java.installations.paths=$JAVA_HOME`.

## Verify

```sh
./gradlew clean build                          # library tests + sample tests

# Major version = Java version + 44; aspectOf() shows ajc wove the aspect.
javap -v -cp build/classes/java/main com.ankur.instrumentor.AppMetricsAdvice | grep -E 'major version|aspectOf\(\)'

docker build -t instrumentor-sample .
scripts/docker-smoke-test.sh instrumentor-sample
```

The sample's `OrderServiceWeavingTest` and the smoke test fail if the advice stops being woven or
stops recording metrics, so a green run shows metric capture still works end to end.

Commit areas: wrapper, library dependencies, sample (Spring Boot and freefair), CI actions, Docker
images, README.

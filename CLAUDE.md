# CLAUDE.md

## Before every push

Vet `README.md` against the commits being pushed (`git log @{u}..HEAD` and their diffs) and fix
anything stale in the same push:

- Versions in the Setup snippet match `build.gradle` and `sample/build.gradle`.
- The Java version it states matches the toolchains, `Dockerfile` and CI.
- Metric names, labels, `@TrackMetrics` attributes and defaults match the code.
- Commands (Gradle tasks, `docker build`, `scripts/docker-smoke-test.sh`) still run as written.
- New or changed behavior is described; removed behavior is no longer mentioned.

If the README needed no changes, say so when reporting the push.

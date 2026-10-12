# CLAUDE.md

## Before every push

Run the `readme-check` skill (`readme-check@ankurjain-skills`, enabled in `.claude/settings.json`).
README claims to keep true here:

- Versions in the Setup snippet match `build.gradle` and `sample/build.gradle`.
- The Java version it states matches the toolchains, `Dockerfile` and CI.
- Metric names, labels, `@TrackMetrics` attributes and defaults match the code.
- Commands (Gradle tasks, `docker build`, `scripts/docker-smoke-test.sh`) still run as written.

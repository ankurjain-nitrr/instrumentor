# syntax=docker/dockerfile:1

# Builds the sample Spring Boot app (and the instrumentor library it weaves in) and runs it on a
# Java 21 JRE. Build from the repository root:
#   docker build -t instrumentor-sample .
#   docker run --rm -p 8080:8080 instrumentor-sample

FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
COPY src src
COPY sample sample
# Tests run in CI; the image build only packages the app.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon :sample:bootJar
# Split the fat jar into layers so dependency layers stay cached across code changes.
RUN java -Djarmode=tools -jar sample/build/libs/sample.jar \
        extract --layers --launcher --destination extracted

FROM eclipse-temurin:21-jre-noble
RUN useradd --system --no-create-home app
WORKDIR /app
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./
USER app
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

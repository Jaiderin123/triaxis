# syntax=docker/dockerfile:1.7
# One image per service, built from the root of the repository (the services need the shared libraries):
#   docker build -f docker/service.Dockerfile --build-arg SERVICE=triage -t triaxis-triage .
# SERVICE is triage, assistant or auth. Base images use exact tags (TECH_STACK section 8).

FROM eclipse-temurin:21.0.10_7-jdk-noble AS build
ARG SERVICE
WORKDIR /src
# The wrapper and the build scripts first, so the Gradle distribution and the dependencies stay cached.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
COPY triaxis-common/build.gradle triaxis-common/
COPY triaxis-security/build.gradle triaxis-security/
COPY triaxis-web/build.gradle triaxis-web/
COPY triaxis-triage-service/build.gradle triaxis-triage-service/
COPY triaxis-assistant-service/build.gradle triaxis-assistant-service/
COPY triaxis-auth-service/build.gradle triaxis-auth-service/
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon -q help
COPY . .
# Tests run in the build of the repository (./gradlew build), not in the image.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon -q :triaxis-${SERVICE}-service:bootJar -x test \
 && find triaxis-${SERVICE}-service/build/libs -name '*.jar' ! -name '*-plain.jar' -exec cp {} /app.jar \;

FROM eclipse-temurin:21.0.10_7-jre-noble
RUN groupadd --system --gid 10001 triaxis && useradd --system --uid 10001 --gid triaxis --no-create-home triaxis
WORKDIR /app
COPY --from=build /app.jar app.jar
USER 10001:10001
# The heap follows the memory limit of the container.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

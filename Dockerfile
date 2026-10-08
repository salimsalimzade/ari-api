# syntax=docker/dockerfile:1

# Build: the Gradle wrapper needs xargs, which the GraalVM image doesn't include.
FROM ghcr.io/graalvm/jdk-community:25 AS build
RUN microdnf install -y findutils && microdnf clean all
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
COPY src src
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon bootJar

# Run: GraalVM publishes JDK images only, so the runtime uses the same one.
FROM ghcr.io/graalvm/jdk-community:25
WORKDIR /app
COPY --from=build /app/build/libs/ari-api-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

# syntax=docker/dockerfile:1

# --- Build: compile and package. Tests run in CI (mvn verify), not in the image build.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests \
    && java -Djarmode=tools -jar target/agent-office-*.jar extract --layers --launcher --destination target/extracted

# --- Runtime: JRE only, non-root, dependencies in their own layers for fast rebuilds.
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 --create-home agentoffice
WORKDIR /app
COPY --from=build /workspace/target/extracted/dependencies/ ./
COPY --from=build /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/target/extracted/application/ ./
USER agentoffice
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

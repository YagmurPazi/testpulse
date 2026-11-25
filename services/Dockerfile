# syntax=docker/dockerfile:1
# Shared image definition for all Java services.
# Build from the repository root: docker build -f services/Dockerfile --build-arg MODULE=ingestion-service .

ARG JAVA_VERSION=21

FROM eclipse-temurin:${JAVA_VERSION}-jdk-noble AS build
ARG MODULE
WORKDIR /workspace
COPY . .
# Tests and static analysis run in CI; the image build only packages the service.
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -ntp package -pl services/${MODULE} -am \
        -DskipTests -Dcheckstyle.skip -Dspotbugs.skip -Djacoco.skip
# Split the jar into layers so dependency layers stay cached when only application code changes.
RUN java -Djarmode=tools -jar services/${MODULE}/target/${MODULE}-*.jar \
        extract --layers --launcher --destination /extracted

FROM eclipse-temurin:${JAVA_VERSION}-jre-noble
RUN groupadd --system app && useradd --system --gid app --uid 10001 app
WORKDIR /app
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./
USER app
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]

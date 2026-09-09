# syntax=docker/dockerfile:1

# ---- build ----------------------------------------------------------------
# Java 25 is the current LTS and what pom.xml targets; see the note there on
# why this is not 26.
FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /build

# Copy the wrapper and POM first so dependency resolution is cached separately
# from the sources. A source-only change then skips the whole download.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline

COPY src/ src/
# Tests run in their own CI job against a real Postgres. Repeating them here
# would need a database inside the image build for no extra signal.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests

# Unpack the fat jar so the JVM sees dependencies and application classes as
# separate layers: dependencies change rarely, application classes every build.
RUN java -Djarmode=tools -jar target/*.jar extract --layers --destination extracted

# ---- run ------------------------------------------------------------------
FROM eclipse-temurin:25-jre-noble AS runtime
WORKDIR /app

# Never root. The image writes nothing outside /tmp.
RUN groupadd --system --gid 1001 watchguru \
 && useradd --system --uid 1001 --gid watchguru --no-create-home watchguru
USER watchguru:watchguru

COPY --from=build --chown=watchguru:watchguru /build/extracted/dependencies/ ./
COPY --from=build --chown=watchguru:watchguru /build/extracted/spring-boot-loader/ ./
COPY --from=build --chown=watchguru:watchguru /build/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=watchguru:watchguru /build/extracted/application/ ./

EXPOSE 8080

# Container-aware heap sizing rather than a fixed -Xmx, so the same image
# behaves on a 512MB and a 4GB allocation.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:InitialRAMPercentage=50 -XX:+ExitOnOutOfMemoryError"

# Deliberately no HEALTHCHECK. The JRE image carries no curl or wget, and
# every sensible way to probe from inside would mean installing one just to
# call a URL the orchestrator can already reach. Point the platform's probe at
# /actuator/health, which is exposed and unauthenticated.

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]

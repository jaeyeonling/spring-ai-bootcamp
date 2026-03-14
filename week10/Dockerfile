# Week 10 — Production Dockerfile
#
# Build:
#   ./gradlew :week10:bootJar
#   docker build -t week10-production .
#
# Or use docker compose (builds automatically):
#   docker compose up --build

# ── Stage 1: Build ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace

# Copy Gradle wrapper and build files first (layer caching)
COPY gradlew .
COPY gradle/ gradle/
COPY build.gradle settings.gradle ./
COPY shared/build.gradle shared/
COPY week10/build.gradle week10/

# Download dependencies (cached unless build files change)
RUN ./gradlew :week10:dependencies --no-daemon 2>/dev/null || true

# Copy source code
COPY shared/src shared/src
COPY data/ data/
COPY week10/src week10/src

# Build the fat JAR
RUN ./gradlew :week10:bootJar --no-daemon -x test

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy FAQ data
COPY --from=build /workspace/data /app/data

# Copy the executable JAR
COPY --from=build /workspace/week10/build/libs/week10-0.0.1-SNAPSHOT.jar app.jar

# Actuator health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

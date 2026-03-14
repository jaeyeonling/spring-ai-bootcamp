# Week 10 — Production Dockerfile
#
# 사전 요구사항: 로컬에서 먼저 JAR 빌드
#   ./gradlew :week10:bootJar
#
# 사용법:
#   # 인프라 + 앱 전체 실행 (로컬 JAR 필요)
#   docker compose up --build
#
#   # 또는 인프라만 먼저 실행하고 앱은 로컬에서 실행
#   docker compose up -d qdrant jaeger
#   ./gradlew :week10:bootRun
#
# 참고: Gradle 8.5의 instrumentation agent가 Linux/aarch64(Apple Silicon) Docker 환경에서
#       libnative-platform-file-events.so SIGSEGV를 유발합니다.
#       이 Dockerfile은 사전 빌드된 JAR을 복사하는 방식을 사용합니다.

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# FAQ 데이터
COPY data/ /app/data

# 사전 빌드된 JAR (./gradlew :week10:bootJar 실행 후 생성됨)
COPY week10/build/libs/week10-0.0.1-SNAPSHOT.jar app.jar

# Actuator health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

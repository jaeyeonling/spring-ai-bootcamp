# M7 — Spring Boot 멀티 스테이지 빌드
#
# 빌드: docker compose up --build (stage2/m7/ 디렉토리에서 실행)
# 단독: docker build -t stage2-m7 -f stage2/m7/Dockerfile . (프로젝트 루트에서 실행)

# Stage 1: 빌드
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Gradle Wrapper ��사 (캐시 레이어)
COPY gradlew .
COPY gradle ./gradle
COPY build.gradle .
COPY settings.gradle .
COPY shared ./shared

# 모듈 소스 복사
COPY stage2/m7 ./stage2/m7
COPY stage2/build.gradle ./stage2/build.gradle

# 빌드 (테스트 스킵)
RUN chmod +x gradlew && ./gradlew :stage2:m7:bootJar -x test --no-daemon

# Stage 2: 실행
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# wget 설치 (healthcheck용)
RUN apk add --no-cache wget

COPY --from=builder /app/stage2/m7/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

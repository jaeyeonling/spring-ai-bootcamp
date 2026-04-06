# 힌트 01: Docker Compose 멀티 서비스 통합

## 기본 구조

```yaml
# docker-compose.yml
version: '3.8'

services:
  app:
    build:
      context: .
      dockerfile: Dockerfile
    ports:
      - "8080:8080"
    environment:
      - OPENAI_API_KEY=${OPENAI_API_KEY}
      - SPRING_PROFILES_ACTIVE=prod
      - SPRING_AI_VECTORSTORE_QDRANT_HOST=qdrant
      - SPRING_AI_VECTORSTORE_QDRANT_PORT=6334
    depends_on:
      qdrant:
        condition: service_healthy
    restart: unless-stopped

  qdrant:
    image: qdrant/qdrant:v1.13.6
    ports:
      - "6333:6333"
      - "6334:6334"
    volumes:
      - qdrant_data:/qdrant/storage
    healthcheck:
      test: ["CMD-SHELL", "curl -sf http://localhost:6333/healthz || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 10s

  # M5 선택 시 Ollama 추가
  ollama:
    image: ollama/ollama:latest
    ports:
      - "11434:11434"
    volumes:
      - ollama_data:/root/.ollama
    profiles:
      - ollama  # docker compose --profile ollama up 으로 선택 실행

volumes:
  qdrant_data:
  ollama_data:
```

## Dockerfile

```dockerfile
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY build/libs/*.jar app.jar

# 데이터 디렉토리
COPY data/ data/

EXPOSE 8080

ENTRYPOINT ["java", \
  "-Xmx512m", \
  "-jar", "app.jar"]
```

## 실행

```bash
# 환경 변수 파일
echo "OPENAI_API_KEY=sk-..." > .env

# 빌드 및 실행
./gradlew bootJar
docker compose up -d

# 로그 확인
docker compose logs -f app

# 상태 확인
curl http://localhost:8080/actuator/health
```

## 서비스 간 통신

Docker Compose 네트워크 내에서 서비스 이름이 호스트명입니다:
- `qdrant` → Qdrant 서버
- `ollama` → Ollama 서버
- `app` → Spring Boot 앱

`localhost`가 아닌 서비스 이름을 사용하세요:

```yaml
# application-prod.yml
spring:
  ai:
    vectorstore:
      qdrant:
        host: qdrant   # localhost → qdrant
        port: 6334
    ollama:
      base-url: http://ollama:11434   # localhost → ollama
```

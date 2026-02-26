# 힌트 03: Docker Compose와 운영 준비도

## 전체 스택

`docker-compose.yml`은 여러 서비스를 오케스트레이션해야 합니다:

```
┌─────────────────────────────────────────────────┐
│                  docker compose                  │
│                                                  │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐      │
│  │   App    │  │  Qdrant  │  │  Jaeger  │      │
│  │  :8080   │  │  :6334   │  │  :16686  │      │
│  └──────────┘  └──────────┘  └──────────┘      │
│                                                  │
│  ┌──────────┐  (선택적)                          │
│  │  Ollama  │                                    │
│  │  :11434  │                                    │
│  └──────────┘                                    │
└─────────────────────────────────────────────────┘
```

## Spring Boot Docker 이미지 빌드

### 옵션 1: Spring Boot Buildpack (가장 쉬움)

```bash
./gradlew :week10:bootBuildImage --imageName=week10-chatbot:latest
```

Cloud Native Buildpack을 사용합니다 — Dockerfile이 필요 없습니다.

### 옵션 2: Dockerfile

```dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY build/libs/week10-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

빌드:
```bash
./gradlew :week10:bootJar
docker build -t week10-chatbot:latest week10/
```

## 헬스 체크와 서비스 의존성

앱이 시작되기 전에 Qdrant가 준비되었는지 확인하기 위해
`depends_on`에 `condition: service_healthy` 사용:

```yaml
services:
  app:
    depends_on:
      qdrant:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 5

  qdrant:
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:6333/healthz || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 5
```

## 데이터 초기화

FAQ는 첫 번째 시작 시 Qdrant에 인제스트되어야 합니다.
두 가지 접근 방식:

### 접근 1: ApplicationRunner (시작 시 자동 인제스트)

```java
@Component
public class DataInitializer implements ApplicationRunner {

    private final IngestionService ingestionService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // 데이터가 이미 로드되었는지 확인
        if (vectorStore.similaritySearch("test").isEmpty()) {
            ingestionService.ingestFile(Path.of("/app/data/faq.md"));
            log.info("FAQ 데이터가 벡터 저장소에 로드되었습니다");
        }
    }
}
```

### 접근 2: REST 엔드포인트 (수동 트리거)

03주차의 `/api/ingest` 엔드포인트를 유지하고 시작 후 호출하세요:

```bash
docker compose up -d
sleep 10  # 앱 시작 대기
curl -X POST http://localhost:8080/api/ingest \
  -F "file=@data/faq.md"
```

## 운영 준비도 체크리스트

`ARCHITECTURE.md`에 이 목록을 사용하세요:

- [ ] **헬스 체크**: `/actuator/health`가 모든 의존성에 대해 UP 보고
- [ ] **우아한 종료**: 앱이 SIGTERM을 올바르게 처리
- [ ] **리소스 제한**: Docker 메모리/CPU 제한 설정
- [ ] **시크릿 관리**: API 키를 환경 변수에서 가져오고 하드코딩하지 않음
- [ ] **로깅**: 운영을 위한 구조화된 JSON 로깅
- [ ] **에러 처리**: API가 스택 트레이스가 아닌 적절한 에러 응답 반환
- [ ] **데이터 영속성**: Qdrant 데이터가 컨테이너 재시작 후에도 유지 (볼륨)
- [ ] **모니터링**: Jaeger에서 트레이스, Actuator에서 메트릭 확인

## 실제 운영에서 부족한 것

`ARCHITECTURE.md`에서 갭에 대해 솔직하게 말하세요. 일반적인 것들:

1. **인증/인가** — API 엔드포인트에 인증 없음
2. **속도 제한** — 남용에 대한 보호 없음
3. **입력 검증** — 프롬프트 인젝션 보호 없음
4. **수평 확장** — 단일 인스턴스만
5. **CI/CD** — 자동화된 빌드/배포 파이프라인 없음
6. **백업/복구** — Qdrant 백업 전략 없음
7. **비용 알림** — 토큰 사용량이 임계값을 초과할 때 알림 없음

이 갭들을 나열하는 것이 엔지니어링 성숙도를 보여줍니다 — 시간이 없어서 구축하지 못했더라도
무엇이 부족하고 왜 중요한지 알고 있다는 것을 보여줍니다.

## 검증 단계

제출 전:

```bash
# 1. 새로 시작
docker compose down -v
docker compose up -d

# 2. 서비스 대기
sleep 15

# 3. 헬스 체크
curl http://localhost:8080/actuator/health

# 4. FAQ 데이터 인제스트
curl -X POST http://localhost:8080/api/ingest -F "file=@../data/faq.md"

# 5. 질문 테스트
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"question": "반품 정책이 어떻게 되나요?"}'

# 6. Jaeger에서 트레이스 확인
open http://localhost:16686

# 7. 평가 실행
./gradlew :week10:test
```

모든 단계가 통과되면 (데모 수준에서) 운영 준비가 된 것입니다.

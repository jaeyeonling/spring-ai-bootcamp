# 힌트 02: Spring Boot 프로덕션 프로파일

개발 환경과 프로덕션 환경의 설정을 분리합니다.

## 프로파일 구조

```
src/main/resources/
├── application.yml          # 공통 설정
├── application-dev.yml      # 개발 환경 (로컬 실행)
└── application-prod.yml     # 프로덕션 (Docker)
```

```yaml
# application.yml (공통)
spring:
  application:
    name: cholog-faq

server:
  port: 8080

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: when-authorized  # 프로덕션에서 외부 노출 제한
```

```yaml
# application-dev.yml
spring:
  ai:
    vectorstore:
      qdrant:
        host: localhost
        port: 6334
        initialize-schema: true
    openai:
      api-key: ${OPENAI_API_KEY}

logging:
  level:
    org.springframework.ai: DEBUG  # 개발 시 상세 로그
    com.jaeyeonling: DEBUG
```

```yaml
# application-prod.yml
spring:
  ai:
    vectorstore:
      qdrant:
        host: qdrant           # Docker 서비스명
        port: 6334
        initialize-schema: false  # 프로덕션: 스키마 자동 생성 비활성화
    openai:
      api-key: ${OPENAI_API_KEY}  # 환경변수에서 주입

logging:
  level:
    root: WARN
    com.jaeyeonling: INFO
  file:
    name: /app/logs/application.log
```

## ETL 초기화 전략

프로덕션에서 매 시작마다 ETL을 재실행하지 않도록:

```java
@Component
public class DataInitializer {

    @Value("${app.etl.force-reingest:false}")
    private boolean forceReingest;

    @PostConstruct
    public void init() {
        if (forceReingest || isQdrantEmpty()) {
            log.info("ETL 시작...");
            ingestionService.ingest();
        } else {
            log.info("Qdrant 데이터 존재 — ETL 건너뜀");
        }
    }

    private boolean isQdrantEmpty() {
        return vectorStore.similaritySearch(
            SearchRequest.query("test").withTopK(1)
        ).isEmpty();
    }
}
```

최초 배포 시:
```bash
# ETL 강제 실행
docker compose run -e APP_ETL_FORCE_REINGEST=true app
```

## 환경변수 관리

```bash
# .env 파일 (절대 커밋하지 마세요)
OPENAI_API_KEY=sk-...

# .env.example (커밋용 템플릿)
OPENAI_API_KEY=your-api-key-here
```

```bash
# .gitignore에 반드시 추가
.env
*.env.local
```

## 배포 후 검증

```bash
# 헬스체크
curl http://localhost:8080/actuator/health

# 기본 동작 확인
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"question": "반품 기간이 얼마나 되나요?"}'

# 정확도 측정 (100개 테스트)
./gradlew test --tests "*EvaluationTest*"
```

---
name: observability
description: Spring AI 관측성 (Micrometer, OpenTelemetry, Actuator, 환각 탐지) 가이드
---

# Observability Guide

## 왜 관측성이 필요한가?

RAG 파이프라인에서 "200 OK인데 답변이 틀렸다"는 상황이 발생합니다.
어디서 문제가 생겼는지 추적할 수 없으면 디버깅이 불가능합니다.

```
[HTTP 요청] → [쿼리 변환] → [벡터 검색] → [ReRank] → [LLM 생성] → [응답]
                                                          ^
                                                          |
                                                    여기서 환각 발생?
                                              아니면 검색이 잘못됐나?
```

## 세 가지 관측성 축

### 1. 메트릭 (Metrics) - "무엇이 일어나고 있나?"

시간 경과에 따른 수치 데이터.

```java
@Component
public class MetricsService {
    private final MeterRegistry meterRegistry;

    // Timer: 검색 지연 시간
    public void recordSearchLatency(long durationMs) {
        Timer.builder("rag.search.latency")
            .description("벡터 검색 소요 시간")
            .register(meterRegistry)
            .record(Duration.ofMillis(durationMs));
    }

    // Counter: 토큰 사용량
    public void recordTokenUsage(long tokens) {
        Counter.builder("rag.llm.token.usage")
            .description("LLM 토큰 사용량")
            .register(meterRegistry)
            .increment(tokens);
    }

    // Gauge: 답변 품질 점수
    public void updateQualityScore(double score) {
        Gauge.builder("rag.answer.quality.score", () -> score)
            .description("최신 답변 품질 점수")
            .register(meterRegistry);
    }
}
```

### 2. 트레이싱 (Tracing) - "어떤 경로로 흘렀나?"

요청의 전체 생명주기를 추적.

```yaml
# application.yml
management:
  tracing:
    sampling:
      probability: 1.0
  otlp:
    tracing:
      endpoint: http://localhost:4318/v1/traces
```

### 3. 로깅 (Logging) - "세부 내용은?"

구조화된 로그로 검색 쿼리, 결과, 프롬프트 기록.

## Spring Boot Actuator

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,info,prometheus
  endpoint:
    health:
      show-details: always
```

### 엔드포인트

- `GET /actuator/health` - 시스템 상태 (OpenAI, Qdrant 연결 상태)
- `GET /actuator/metrics` - 사용 가능한 메트릭 목록
- `GET /actuator/metrics/rag.search.latency` - 특정 메트릭 상세

## Spring AI 자동 계측

Spring AI는 Micrometer를 통해 자동으로 메트릭을 생성합니다:

```yaml
spring:
  ai:
    chat:
      observations:
        include-input: true
        include-output: true
```

자동 생성되는 메트릭:
- `spring.ai.chat.client.duration` - ChatClient 호출 시간
- `spring.ai.embedding.client.duration` - 임베딩 호출 시간

## OpenTelemetry 연동

```groovy
// build.gradle
implementation 'io.micrometer:micrometer-tracing-bridge-otel'
implementation 'io.opentelemetry:opentelemetry-exporter-otlp'
```

### Jaeger로 트레이스 시각화

```bash
# Jaeger 실행
docker run -d -p 4317:4317 -p 4318:4318 -p 16686:16686 jaegertracing/all-in-one:latest

# Jaeger UI: http://localhost:16686
```

## 환각 탐지 (Week 05)

### 배치 평가 서비스

```java
@Service
public class EvaluationService {
    private final ChatClient chatClient;
    private final MetricsService metricsService;

    public void runBatchEvaluation(List<TestQuestion> questions) {
        RelevancyEvaluator relevancyEval = new RelevancyEvaluator(chatClient);

        for (TestQuestion q : questions) {
            String answer = ragService.ask(q.question());
            boolean relevant = relevancyEval.evaluate(q.question(), answer);

            if (!relevant) {
                metricsService.incrementHallucination();
                log.warn("환각 탐지: Q={}, A={}", q.question(), answer);
            }
        }
    }
}
```

### 인시던트 분석

트레이스와 메트릭을 사용하여 근본 원인을 파악:
1. **검색 실패**: 검색 스팬에서 반환된 문서가 관련 없음
2. **생성 실패**: 좋은 컨텍스트에도 불구하고 LLM이 환각

## 커스텀 RAG 메트릭 목록

| 메트릭 | 타입 | 설명 |
|--------|------|------|
| `rag.search.latency` | Timer | 벡터 검색 소요 시간 |
| `rag.search.results.count` | Distribution | 반환된 문서 수 |
| `rag.llm.token.usage` | Counter | 토큰 사용량 |
| `rag.llm.token.cost` | Counter | USD 예상 비용 |
| `rag.answer.quality.score` | Gauge | 답변 품질 점수 |
| `rag.hallucination.detected` | Counter | 환각 탐지 수 |

## 관련 스킬

- `spring-ai-api`: Spring AI API 상세
- `rag-pipeline`: RAG 전체 흐름

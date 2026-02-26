# 힌트 01: Spring AI + Micrometer — 자동 계측 메트릭

## Spring AI가 무료로 제공하는 것

Spring AI 1.1.x는 Micrometer observations로 `ChatModel`과 `EmbeddingModel`을 자동 계측합니다.
기본 LLM 메트릭을 위한 커스텀 계측을 작성할 필요가 없습니다 — 이미 제공됩니다.

### Observation 활성화

Spring AI observations는 기본적으로 활성화되어 있지만, 입출력 로깅을 위해
명시적으로 선택해야 합니다 (프롬프트에 민감한 데이터가 포함될 수 있으므로):

```yaml
spring:
  ai:
    chat:
      client:
        observations:
          include-input: true    # observation에 프롬프트 내용 기록
          include-output: true   # observation에 완성 내용 기록
```

### 자동으로 제공되는 메트릭

observation이 활성화되면 Spring AI는 다음 Micrometer 메트릭을 게시합니다:

| 메트릭 | 설명 |
|--------|-------------|
| `gen_ai.client.token.usage` | 호출당 토큰 사용량 (프롬프트 + 완성) |
| `gen_ai.client.operation.duration` | 각 LLM 호출의 지속 시간 |

이 메트릭에는 다음 태그가 포함됩니다:
- `gen_ai.operation.name` — 예: `chat`, `embedding`
- `gen_ai.request.model` — 예: `gpt-4o-mini`
- `gen_ai.response.model` — 실제 사용된 모델 (요청과 다를 수 있음)
- `gen_ai.usage.input_tokens` — 프롬프트 토큰
- `gen_ai.usage.output_tokens` — 완성 토큰

### Actuator를 통해 메트릭 확인

Actuator 의존성 추가 (이미 `build.gradle`에 있음) 및 메트릭 엔드포인트 노출:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, metrics, prometheus
  endpoint:
    health:
      show-details: always
```

그런 다음 쿼리:
```bash
# 모든 메트릭 목록
curl http://localhost:8080/actuator/metrics

# 토큰 사용량 상세
curl http://localhost:8080/actuator/metrics/gen_ai.client.token.usage

# LLM 호출 지속 시간
curl http://localhost:8080/actuator/metrics/gen_ai.client.operation.duration
```

### 커스텀 RAG 메트릭 추가

자동 계측 메트릭은 LLM 호출을 포괄하지만, 전체 RAG 파이프라인을 위한
커스텀 메트릭이 필요합니다:

```java
@Component
public class MetricsService {

    private final MeterRegistry meterRegistry;

    // 타이머
    private final Timer searchLatency;

    // 카운터
    private final Counter hallucinationCounter;

    // 게이지 백킹 값 (AtomicReference<Double> 사용)
    private final AtomicReference<Double> qualityScore = new AtomicReference<>(0.0);

    public MetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        this.searchLatency = Timer.builder("rag.search.latency")
            .description("벡터 저장소에서 문서 검색 시간")
            .register(meterRegistry);

        this.hallucinationCounter = Counter.builder("rag.hallucination.detected")
            .description("탐지된 환각 수")
            .register(meterRegistry);

        Gauge.builder("rag.answer.quality.score", qualityScore, AtomicReference::get)
            .description("최신 답변 품질 점수")
            .register(meterRegistry);
    }

    public void recordSearchLatency(long durationMs) {
        searchLatency.record(Duration.ofMillis(durationMs));
    }

    public void incrementHallucinationCount() {
        hallucinationCounter.increment();
    }

    public void updateQualityScore(double score) {
        qualityScore.set(score);
    }
}
```

### 핵심 개념: Observations vs. 메트릭

- **Observations** (`ObservationRegistry` 경유)는 상위 수준: 단일 계측 포인트에서 메트릭과 트레이스 모두 생성.
- **메트릭** (`MeterRegistry` 경유)은 하위 수준: 카운터, 게이지, 타이머.

Spring AI는 내부적으로 Observations를 사용합니다. 커스텀 RAG 파이프라인 메트릭에는
두 가지 방식을 모두 사용할 수 있습니다. 간단히 시작하려면 직접 `MeterRegistry`를
사용하세요. 같은 계측에서 메트릭과 트레이스를 모두 원하면 `ObservationRegistry`를 사용하세요.

## 시도해볼 것

1. 애플리케이션 시작 후 `/actuator/metrics` 접근
2. ChatClient 호출 후 `gen_ai.client.token.usage` 확인
3. 커스텀 `MetricsService` 추가 및 RAG 메트릭이 나타나는지 확인
4. 검색 전략에 `MetricsService.recordSearchLatency()` 연결

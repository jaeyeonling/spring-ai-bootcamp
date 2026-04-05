# 힌트 03: Micrometer 메트릭과 가드레일

## 비용 & 지연시간 추적

요청마다 토큰 수와 응답 시간을 기록합니다.

```java
@Component
public class RagMetrics {

    private final MeterRegistry registry;
    private final Counter totalRequests;
    private final Counter hallucinationCount;
    private final DistributionSummary promptTokens;
    private final DistributionSummary completionTokens;

    public RagMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.totalRequests = Counter.builder("rag.requests.total").register(registry);
        this.hallucinationCount = Counter.builder("rag.hallucination.count").register(registry);
        this.promptTokens = DistributionSummary.builder("rag.tokens.prompt").register(registry);
        this.completionTokens = DistributionSummary.builder("rag.tokens.completion").register(registry);
    }

    public void record(ChatResponse response, boolean hallucination) {
        totalRequests.increment();
        if (hallucination) hallucinationCount.increment();

        Usage usage = response.getMetadata().getUsage();
        promptTokens.record(usage.getPromptTokens());
        completionTokens.record(usage.getCompletionTokens());
    }
}
```

Actuator로 메트릭 확인:
```bash
curl http://localhost:8080/actuator/metrics/rag.tokens.prompt
```

## OpenTelemetry 트레이싱

Spring AI는 OpenTelemetry를 자동 지원합니다.
RAG 파이프라인 전체 — 검색 → LLM 호출 → 응답 — 를 하나의 trace로 추적합니다.

```yaml
# application.yml
management:
  tracing:
    enabled: true
    sampling:
      probability: 1.0  # 개발 환경: 전체 샘플링
```

```xml
<!-- build.gradle -->
implementation 'io.micrometer:micrometer-tracing-bridge-otel'
implementation 'io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter'
```

로컬에서 Zipkin으로 trace 확인:
```bash
docker run -p 9411:9411 openzipkin/zipkin
```
`http://localhost:9411`에서 검색→LLM 전체 경로 확인 가능.

## 가드레일 구현

FAQ에 없는 내용에 대해 "모르겠습니다"를 강제합니다.

```java
private static final String SYSTEM_PROMPT = """
    당신은 초록 코퍼레이션 고객지원 챗봇입니다.

    규칙:
    1. 아래 컨텍스트에 있는 내용만 답변하세요.
    2. 컨텍스트에 없는 내용은 추측하지 마세요.
    3. 확인할 수 없는 경우 반드시 이렇게 답하세요:
       "죄송합니다, 해당 내용은 안내드리기 어렵습니다. 고객센터(1234-5678)로 문의해 주세요."
    4. 정치, 종교, 개인정보 관련 질문에는 답변하지 마세요.

    컨텍스트:
    {context}
    """;
```

가드레일 효과를 측정하세요:
```
가드레일 없음: "모르겠습니다" 응답 5% (나머지 95%는 정보 없어도 만들어냄)
가드레일 적용: "모르겠습니다" 응답 ?%, 환각률 ?%
```

## 답변 범위 위반 탐지

```java
public boolean isOutOfScope(String question) {
    String check = chatClient.prompt()
        .user("""
            다음 질문이 이커머스 고객지원 범위를 벗어나는지 판단하세요.
            범위 외: 정치, 종교, 개인정보, 의료, 법률 조언, 경쟁사 비교
            JSON: {"out_of_scope": true/false}

            질문: %s
            """.formatted(question))
        .call()
        .content();

    return parseBoolean(check, "out_of_scope");
}
```

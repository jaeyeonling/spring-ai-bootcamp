# 힌트 01: 비용 추적 — 어디서 얼마나 쓰는가

절감하기 전에 현재 비용을 정확히 파악하세요.

## 요청별 토큰 추적

```java
@Component
public class CostTracker {

    // OpenAI 가격 (per 1M tokens, USD)
    // 최신 가격은 https://openai.com/api/pricing 에서 확인하세요
    private static final Map<String, double[]> TOKEN_COST = Map.of(
        "gpt-4.1",      new double[]{2.00, 8.00},    // [입력, 출력]
        "gpt-4.1-mini", new double[]{0.40, 1.60},
        "gpt-4.1-nano", new double[]{0.10, 0.40},
        "gpt-4o-mini",  new double[]{0.15, 0.60}     // 비교용 (이전 기본 모델)
    );

    private final MeterRegistry registry;

    public void record(String model, Usage usage) {
        double[] prices = TOKEN_COST.getOrDefault(model, new double[]{0, 0});
        double cost = (usage.getPromptTokens() * prices[0]
                     + usage.getCompletionTokens() * prices[1]) / 1_000_000;

        registry.counter("llm.cost.usd", "model", model).increment(cost);
        registry.summary("llm.tokens.prompt", "model", model)
                .record(usage.getPromptTokens());
        registry.summary("llm.tokens.completion", "model", model)
                .record(usage.getCompletionTokens());

        log.debug("[비용] {} | 입력 {}토큰 + 출력 {}토큰 = ${:.6f}",
            model, usage.getPromptTokens(), usage.getCompletionTokens(), cost);
    }
}
```

## 실제 비용 계산 예시

Stage 1에서 테스트 100개를 돌렸을 때:

```
gpt-4.1-nano 기준 (이 프로젝트 기본 모델):
- 평균 프롬프트 토큰: 1,500 (컨텍스트 + 질문)
- 평균 완성 토큰: 200
- 요청당 비용: (1500 × $0.10 + 200 × $0.40) / 1,000,000 = $0.000230
- 100개 테스트: $0.023
- 하루 1,000요청: $0.230
- 월간: ~$7

gpt-4.1 사용 시:
- 요청당 비용: (1500 × $2.00 + 200 × $8.00) / 1,000,000 = $0.004600
- 월간: ~$140 (20배 차이)
```

이 계산으로 현재 상황을 파악한 후, 절감 전략을 선택하세요.

## Actuator로 실시간 확인

```bash
# 누적 비용
curl http://localhost:8080/actuator/metrics/llm.cost.usd

# 평균 프롬프트 토큰
curl http://localhost:8080/actuator/metrics/llm.tokens.prompt
```

## 비용이 예상보다 높다면

흔한 원인:

1. **컨텍스트가 너무 큼** — RAG에서 top-K를 줄이거나 청크 크기 축소
2. **시스템 프롬프트가 너무 긺** — 불필요한 설명 제거
3. **같은 질문 반복 호출** — 캐싱 필요 (HINT_03)
4. **비싼 모델 사용** — 모델 라우팅 (HINT_02)

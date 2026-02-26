# 힌트 02: Week 1 vs Week 10 비교

## 왜 중요한가

비교가 가장 중요한 제출물입니다. 배운 모든 것이 측정 가능한 영향을 미쳤다는 것을 증명합니다.
비교 없이는 10주가 단순한 연습의 연속에 불과합니다.

## 비교 실행

### 1단계: Week 01 동작 재현

단순한 방식으로 질문에 답하는 "week 01 모드"가 필요합니다:

```java
@Service
@Profile("week01-baseline")
public class Week01BaselineService {

    private final ChatClient chatClient;

    public Week01BaselineService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public String answer(String question) {
        // Week 01 방식: 전체 FAQ를 프롬프트에 넣기
        String faq = Files.readString(Path.of("../data/faq.md"));

        return chatClient.prompt()
            .system("이 FAQ를 기반으로 답하세요:\n" + faq)
            .user(question)
            .call()
            .content();
    }
}
```

또는 더 간단히: 실제 week01 프로젝트를 사용하세요. 두 개를 병렬로 실행하세요:

```bash
# 터미널 1: Week 01을 8081 포트에서
./gradlew :week01:bootRun --args='--server.port=8081'

# 터미널 2: Week 10을 8080 포트에서
./gradlew :week10:bootRun
```

### 2단계: 같은 질문을 두 버전 모두에 실행

`test_questions.json`의 30개 질문과 20개의 추가 엣지 케이스를 사용하세요:

```java
public void runComparison() {
    List<TestQuestion> questions = loadTestQuestions("../data/test_questions.json");

    List<ComparisonResult> results = new ArrayList<>();
    for (TestQuestion q : questions) {
        // Week 01
        long start1 = System.currentTimeMillis();
        String answer1 = week01Service.answer(q.question());
        long latency1 = System.currentTimeMillis() - start1;

        // Week 10
        long start10 = System.currentTimeMillis();
        String answer10 = week10Service.answer(q.question());
        long latency10 = System.currentTimeMillis() - start10;

        // 둘 다 평가
        double relevancy1 = evaluate(q, answer1);
        double relevancy10 = evaluate(q, answer10);

        results.add(new ComparisonResult(q, answer1, answer10,
            relevancy1, relevancy10, latency1, latency10));
    }

    printComparisonTable(results);
}
```

### 3단계: 다음 메트릭 측정

| 메트릭 | 측정 방법 |
|--------|---------------|
| 답변 정확도 | RelevancyEvaluator 통과율 |
| 충실도 | FactCheckingEvaluator (답변이 컨텍스트에 충실한가?) |
| 관련성 | 평균 관련성 점수 (0.0 ~ 1.0) |
| 평균 지연시간 | 각 호출 주변에 System.currentTimeMillis() |
| 토큰/쿼리 | ChatResponse.getMetadata().getUsage().getTotalTokens() |
| 비용/1K 쿼리 | tokens * price_per_token * 1000 |

### 4단계: 표 채우기

```markdown
| 메트릭 | Week 01 | Week 10 | 개선 |
|--------|---------|---------|-------------|
| 답변 정확도 | 45% | 82% | +37pp |
| 충실도 | N/A | 91% | - |
| 관련성 | 0.48 | 0.85 | +77% |
| 평균 지연시간 (ms) | 1200 | 1800 | -50% (느리지만 더 정확) |
| 평균 토큰/쿼리 | 3500 | 1200 | -66% (타깃화된 컨텍스트) |
| 비용/1K 쿼리 | $2.10 | $0.72 | -66% |
```

### 5단계: 3-5개의 쇼케이스 질문 선택

Week 10이 Week 01보다 명확히 우수한 질문을 찾으세요:

**좋은 쇼케이스 질문:**
- 다면적인 질문 ("세일 상품 반품 정책과 배송비는 누가 부담하나요?")
- 구체적인 숫자가 필요한 질문 ("실버 등급 요금은 얼마인가요?")
- 툴 호출이 필요한 질문 ("주문 #ORD-2024-001 상태")
- Week 01이 환각을 일으키는 질문

각 질문에 대해 두 답변을 나란히 놓고 Week 10이 왜 더 나은지 설명하세요.

## 지연시간 트레이드오프

Week 10은 Week 01보다 **느릴** 가능성이 높습니다:
- 벡터 검색이 ~100-200ms 추가
- 재순위화가 ~500-1000ms 추가
- 툴 호출이 LLM 왕복을 한 번 더 추가

이것은 예상된 것입니다. 트레이드오프: 지연시간이 적당히 늘어나는 대신 정확도가 크게 향상됩니다.
실제 세계에서 2초에 정확한 답변이 1초에 틀린 답변보다 낫습니다.

이 트레이드오프를 명시적으로 문서화하세요 — 엔지니어링 성숙도를 보여줍니다.

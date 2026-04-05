# 힌트 02: Spring AI 평가 API — EvaluationRunner, FactCheckingEvaluator

직접 LLM으로 채점하는 것도 좋지만, Spring AI에 평가 도구가 내장되어 있습니다.

## RelevancyEvaluator

답변이 질문과 관련 있는지 판단합니다.

```java
@Autowired
RelevancyEvaluator relevancyEvaluator;

@Test
void 답변_관련성_평가() {
    // RAG 실행
    ChatResponse response = chatClient.prompt()
        .advisors(new QuestionAnswerAdvisor(vectorStore))
        .user("반품 기간이 얼마나 되나요?")
        .call()
        .chatResponse();

    // 관련성 평가
    EvaluationRequest evalRequest = new EvaluationRequest(
        "반품 기간이 얼마나 되나요?",
        response  // 검색 컨텍스트와 답변 포함
    );
    EvaluationResponse evalResponse = relevancyEvaluator.evaluate(evalRequest);

    assertThat(evalResponse.isPass()).isTrue();
}
```

## FactCheckingEvaluator

답변이 검색 문서에 근거하는지 판단합니다. 환각 탐지에 사용합니다.

```java
@Autowired
FactCheckingEvaluator factCheckingEvaluator;

void 환각_탐지(String question, ChatResponse response) {
    EvaluationRequest request = new EvaluationRequest(question, response);
    EvaluationResponse result = factCheckingEvaluator.evaluate(request);

    if (!result.isPass()) {
        log.warn("환각 탐지: {} — {}", question, result.getFeedback());
        metricsService.incrementHallucinationCount();
    }
}
```

## EvaluationRunner로 배치 평가

```java
@Component
public class BatchEvaluator {

    public EvaluationReport runBatch(List<TestQuestion> questions) {
        List<EvaluationResult> results = questions.stream()
            .map(q -> {
                ChatResponse response = rag.ask(q.question());

                boolean relevancy = relevancyEvaluator
                    .evaluate(new EvaluationRequest(q.question(), response)).isPass();
                boolean factual = factCheckingEvaluator
                    .evaluate(new EvaluationRequest(q.question(), response)).isPass();

                return new EvaluationResult(q, relevancy, factual);
            })
            .toList();

        return EvaluationReport.from(results);
    }
}

public record EvaluationReport(
    int total,
    int relevancyPass,
    int factualPass,
    double relevancyRate,
    double factualRate
) {
    public static EvaluationReport from(List<EvaluationResult> results) {
        return new EvaluationReport(
            results.size(),
            (int) results.stream().filter(EvaluationResult::relevancy).count(),
            (int) results.stream().filter(EvaluationResult::factual).count(),
            results.stream().mapToInt(r -> r.relevancy() ? 1 : 0).average().orElse(0),
            results.stream().mapToInt(r -> r.factual() ? 1 : 0).average().orElse(0)
        );
    }
}
```

## 비용 주의

평가 1회 = LLM 호출 1-2회 추가.
100개 질문 평가 = 200-300회 LLM 호출 → 실시간으로 돌리면 응답 지연 28배.

**배치와 실시간을 분리하세요:**

```java
// 실시간 API: 평가 없이 응답만
@PostMapping("/api/chat")
public ChatResponse chat(@RequestBody ChatRequest request) {
    return ragService.ask(request.question());
}

// 배치 평가: 스케줄러로 주기적 실행
@Scheduled(cron = "0 0 2 * * *")  // 매일 새벽 2시
public void runDailyEvaluation() {
    EvaluationReport report = batchEvaluator.runBatch(testQuestions);
    log.info("일간 평가: 관련성 {}%, 사실성 {}%", report.relevancyRate(), report.factualRate());
}
```

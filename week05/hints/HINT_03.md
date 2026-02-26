# 힌트 03: 모니터링으로서의 평가 — 자동 환각 탐지

## 아이디어

관찰 가능성은 지연시간과 업타임만의 문제가 아닙니다. AI 시스템에서는 **답변 품질**도
모니터링해야 합니다. 200ms에 응답하지만 환각을 일으키는 응답은 느린 정확한 답변보다
더 나쁩니다.

접근 방식: 주기적으로(또는 모든 응답에) 배치 평가를 실행하고, 답변에 점수를 매기고,
점수를 메트릭으로 내보냅니다. 점수가 임계값 아래로 떨어지면 알림을 보냅니다.

## Spring AI 평가기

Spring AI 1.0은 RAG 품질을 평가하기 위한 평가기 인터페이스를 제공합니다:

### RelevancyEvaluator

검색된 문서가 사용자 질문과 관련 있는지 확인합니다.

```java
import org.springframework.ai.chat.evaluation.RelevancyEvaluator;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;

@Component
public class EvaluationService {

    private final RelevancyEvaluator relevancyEvaluator;

    public EvaluationService(ChatClient.Builder chatClientBuilder) {
        this.relevancyEvaluator = new RelevancyEvaluator(chatClientBuilder);
    }

    public EvaluationResponse evaluateRelevancy(
            String userQuery,
            String llmAnswer,
            List<Document> retrievedDocuments) {

        EvaluationRequest request = new EvaluationRequest(
            userQuery,
            retrievedDocuments,
            llmAnswer
        );

        return relevancyEvaluator.evaluate(request);
    }
}
```

`EvaluationResponse`에는 다음이 포함됩니다:
- `isPass()` — boolean, 관련성 검사 통과 여부?
- `getScore()` — float 점수 (0.0 ~ 1.0)
- `getFeedback()` — 점수 설명

### FactCheckingEvaluator

답변이 제공된 컨텍스트와 사실적으로 일치하는지 확인합니다 (즉, 환각이 없는지).
**Spring AI 1.1.x 이상**에서 사용 가능합니다.

```java
import org.springframework.ai.chat.evaluation.FactCheckingEvaluator;

@Bean
public FactCheckingEvaluator factCheckingEvaluator(ChatClient.Builder chatClientBuilder) {
    return new FactCheckingEvaluator(chatClientBuilder);
}

// 사용법은 RelevancyEvaluator와 동일:
// evaluator.evaluate(new EvaluationRequest(query, documents, answer));
```

## 배치 평가 루프

모든 테스트 질문을 파이프라인으로 실행하고 각각 점수 매기기:

```java
@Component
public class BatchEvaluator {

    private final RelevancyEvaluator relevancyEvaluator;
    private final FactCheckingEvaluator factCheckingEvaluator;
    private final MetricsService metricsService;

    public List<EvaluationResult> runBatchEvaluation(List<TestQuestion> questions) {
        List<EvaluationResult> results = new ArrayList<>();

        for (TestQuestion q : questions) {
            // TODO: 이 질문으로 전체 RAG 파이프라인 실행
            // 1. 문서 검색
            // 2. 답변 생성

            // TODO: 답변 점수화
            // EvaluationResponse relevancy = relevancyEvaluator.evaluate(...);
            // EvaluationResponse factCheck = factCheckingEvaluator.evaluate(...);

            // TODO: 환각 플래그
            // if (!factCheck.isPass()) {
            //     metricsService.incrementHallucinationCount();
            //     log.warn("환각 탐지: {}", q.question());
            // }

            // TODO: 품질 메트릭 기록
            // metricsService.updateQualityScore(relevancy.getScore());

            // TODO: 결과 수집
            // results.add(new EvaluationResult(q, answer, relevancy, factCheck));
        }

        return results;
    }

    public record EvaluationResult(
        TestQuestion question,
        String answer,
        EvaluationResponse relevancy,
        EvaluationResponse factCheck
    ) {
        public boolean isHallucination() {
            return !factCheck.isPass();
        }
    }
}
```

## 평가 점수를 메트릭으로 내보내기

평가 결과를 모니터링 대시보드에서 볼 수 있도록:

```java
// 배치 평가 완료 후:
double avgRelevancy = results.stream()
    .mapToDouble(r -> r.relevancy().getScore())
    .average()
    .orElse(0.0);

double hallucinationRate = results.stream()
    .filter(EvaluationResult::isHallucination)
    .count() / (double) results.size();

metricsService.updateQualityScore(avgRelevancy);

// 환각 비율 게이지도 만들 수 있습니다:
// Gauge.builder("rag.hallucination.rate", () -> hallucinationRate)
//     .register(meterRegistry);
```

## 배치 평가 스케줄링

품질 저하를 잡기 위해 주기적으로 평가 실행:

```java
@Component
public class ScheduledEvaluator {

    private final BatchEvaluator batchEvaluator;

    @Scheduled(fixedRate = 3600000) // 매시간
    public void runScheduledEvaluation() {
        // TODO: 테스트 질문 로드
        // TODO: 배치 평가 실행
        // TODO: 결과 요약 로그
        // TODO: 환각 비율이 임계값 초과 시 알림
    }
}
```

## 인시던트 분석에 평가 연결

나쁜 답변 조사 시:

1. 요청에 대한 트레이스 찾기 (트레이스 ID 또는 타임스탬프로)
2. 검색 스팬 확인: 관련 문서가 반환됐나요?
3. 생성 스팬 확인: LLM이 컨텍스트를 올바르게 사용했나요?
4. 특정 질문에 대해 평가기 실행하여 품질 점수 얻기
5. 기준선 점수와 비교하여 회귀인지 확인

이것이 5분짜리 인시던트 조사 워크플로우입니다.

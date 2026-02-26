# 힌트 03: Strategy 패턴 — 코드 변경 없이 검색 방식 교체

## 패턴

검색 방법이 3가지 이상 있습니다. 코드를 편집하지 않고 설정으로 전환하고 싶습니다.
이것이 고전적인 **Strategy 패턴**입니다.

```
                    +--------------------+
                    | RetrievalStrategy  |  <-- 인터페이스
                    |--------------------|
                    | retrieve(query, k) |
                    +--------------------+
                           ^   ^   ^
                           |   |   |
              +------------+   |   +-------------+
              |                |                  |
   +------------------+  +------------------+  +---------------------+
   | BaselineRetrieval|  | ReRankingRetrieval|  | QueryTransformRetrieval|
   +------------------+  +------------------+  +---------------------+
```

## Spring 프로파일 사용

각 구현에 `@Profile` 애노테이션을 붙입니다. 한 번에 하나만 활성화됩니다:

```java
public interface RetrievalStrategy {
    List<Document> retrieve(String query, int topK);
}

@Component
@Profile("baseline")
public class BaselineRetrieval implements RetrievalStrategy {
    // ...
}

@Component
@Profile("reranker")
public class ReRankingRetrieval implements RetrievalStrategy {
    // ...
}

@Component
@Profile("query-transform")
public class QueryTransformRetrieval implements RetrievalStrategy {
    // ...
}
```

활성 프로파일을 변경하여 전략을 전환:

```yaml
# application.yml
spring:
  profiles:
    active: reranker   # baseline, reranker, query-transform으로 변경 가능
```

또는 커맨드라인:
```bash
./gradlew :week04:bootRun --args='--spring.profiles.active=reranker'
```

## 대안: 설정 프로퍼티

동일한 애플리케이션에서 모든 전략을 실행하고 싶으면(예: 평가용) 프로파일 대신 프로퍼티 사용:

```java
@Component
public class RetrievalStrategyRouter {

    private final Map<String, RetrievalStrategy> strategies;

    public RetrievalStrategyRouter(List<RetrievalStrategy> allStrategies) {
        this.strategies = allStrategies.stream()
            .collect(Collectors.toMap(
                s -> s.getClass().getSimpleName().replace("Retrieval", "").toLowerCase(),
                Function.identity()
            ));
    }

    public RetrievalStrategy getStrategy(String name) {
        return strategies.getOrDefault(name, strategies.get("baseline"));
    }

    public Map<String, RetrievalStrategy> getAllStrategies() {
        return Collections.unmodifiableMap(strategies);
    }
}
```

이것은 평가 보고서에 유용합니다 — 모든 전략을 순회하며 결과를 비교할 수 있습니다.

## 평가 루프

```java
@Component
public class RetrievalEvaluator {

    private final RetrievalStrategyRouter router;

    public void evaluate(List<TestQuestion> testQuestions) {
        Map<String, RetrievalStrategy> strategies = router.getAllStrategies();

        for (var entry : strategies.entrySet()) {
            String name = entry.getKey();
            RetrievalStrategy strategy = entry.getValue();

            int top1Hits = 0;
            int top5Hits = 0;
            long totalLatency = 0;

            for (TestQuestion q : testQuestions) {
                long start = System.currentTimeMillis();
                List<Document> results = strategy.retrieve(q.question(), 5);
                totalLatency += System.currentTimeMillis() - start;

                if (containsAnswer(results.subList(0, 1), q.expectedAnswer())) {
                    top1Hits++;
                }
                if (containsAnswer(results, q.expectedAnswer())) {
                    top5Hits++;
                }
            }

            System.out.printf("| %-18s | %5.1f%% | %5.1f%% | %6d ms |%n",
                name,
                100.0 * top1Hits / testQuestions.size(),
                100.0 * top5Hits / testQuestions.size(),
                totalLatency / testQuestions.size());
        }
    }

    private boolean containsAnswer(List<Document> docs, String expected) {
        // TODO: 구현 — 예상 답변의 핵심 구문이 문서 내용에 포함되는지 확인.
        // 정확한 문자열 매칭 대신 임베딩 유사도 사용을 고려.
        throw new UnsupportedOperationException("구현해주세요");
    }
}
```

## 전략 결합

최선의 결과는 종종 기법을 결합할 때 나옵니다:

```java
@Component
@Profile("combined")
public class CombinedRetrieval implements RetrievalStrategy {

    private final QueryTransformRetrieval queryTransform;
    private final LlmReRanker reranker;
    private final VectorStore vectorStore;

    @Override
    public List<Document> retrieve(String query, int topK) {
        // 1단계: 쿼리 변환
        // 2단계: 변환된 쿼리로 검색 (상위 20개)
        // 3단계: 결과 재순위화
        // 4단계: 상위 K개 반환
        throw new UnsupportedOperationException("구현해주세요");
    }
}
```

이 파이프라인 — 변환, 광범위 검색, 재순위화 — 은 가장 일반적인 운영 패턴입니다.

## 종합하기

1. 3가지 전략 모두 구현 (선택적으로 combined도)
2. 평가 루프 작성
3. `test_questions.json` 대비 평가 실행
4. 보고서에 비교 표 채우기
5. 가장 성능이 좋은 전략으로 기본값 전환

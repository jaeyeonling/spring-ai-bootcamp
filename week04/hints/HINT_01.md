# 힌트 01: ReRanking — 최선의 결과를 상위로 올리기

## 벡터 검색만으로는 부족한 이유

벡터 유사도 검색은 **이중 인코더** 모델을 사용합니다: 쿼리와 각 문서가 독립적으로
임베딩된 후 코사인 유사도로 비교됩니다. 빠르지만 얕습니다 — 미묘한 관련성 신호를 놓칩니다.

**재순위화기(크로스 인코더)**는 쿼리와 문서를 **함께** 보면서 더 깊게 관계를 이해합니다.
느리지만 관련성 점수 매기기에 훨씬 정확합니다.

## 2단계 패턴

```
사용자 쿼리
    |
    v
[1단계: 벡터 검색 — 상위 20개 후보 검색]   <-- 빠름, 광범위
    |
    v
[2단계: 재순위화기 — 각 후보를 쿼리 대비 점수화]  <-- 느림, 정밀
    |
    v
[상위 K개 재순위화된 결과 반환]
```

이것이 표준 운영 패턴입니다. 1단계는 저렴한 재현율 필터; 2단계는 비싼 정밀도 필터입니다.

## Spring AI의 DocumentPostProcessor

Spring AI에는 내장 재순위화기 구현이 없지만, 검색된 문서를 후처리하기 위한
`DocumentPostProcessor` 인터페이스를 제공합니다:

```java
public interface DocumentPostProcessor {
    List<Document> process(List<Document> documents);
}
```

**중요한 제한**: `process()`는 쿼리 텍스트를 받지 않아 이 인터페이스를 통한
순수 LLM 기반 재순위화가 어색합니다. 실용적인 해결책은 검색과 재순위화를 모두
캡슐화하는 자체 `RetrievalStrategy`를 만드는 것입니다:

```java
// 권장 방식: RetrievalStrategy를 직접 확장
public class ReRankingRetrieval implements RetrievalStrategy {

    @Override
    public List<Document> retrieve(String query, int topK) {
        // 1단계: 더 큰 후보 집합 검색
        List<Document> candidates = vectorStore.similaritySearch(
            SearchRequest.builder().query(query).topK(20).build()
        );
        // 2단계: 쿼리 + 후보로 재순위화
        return rerank(query, candidates, topK);
    }
}
```

Spring AI 어드바이저 체인에 연결하고 싶으면 `DocumentPostProcessor`를 구현할 수 있지만,
쿼리를 별도로 저장해야 합니다 (예: 스레드-로컬 또는 요청 범위 빈).

## LLM 기반 재순위화기 구현

전용 재순위화기 모델(Cohere Rerank 등)은 별도 API 설정이 필요하므로,
LLM 자체를 재순위화에 활용할 수 있습니다. 실용적이고 잘 작동하는 방식입니다:

```java
@Component
public class LlmReRanker {

    private final ChatClient chatClient;

    public LlmReRanker(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * LLM에게 쿼리에 대한 각 문서의 관련성을 점수화하도록 요청합니다.
     * 관련성 점수 순으로 재정렬된 문서를 반환합니다 (높은 점수 먼저).
     */
    public List<Document> rerank(String query, List<Document> candidates, int topK) {
        // TODO: 각 문서를 점수화하도록 LLM에 요청하는 프롬프트 구성.
        // 예시 프롬프트 구조:
        //
        // "쿼리: '{query}'에 대해
        //  각 문서의 관련성을 0.0에서 1.0으로 점수화하세요.
        //  같은 순서로 점수 JSON 배열만 반환하세요. 예: [0.9, 0.3, 0.7].
        //
        //  문서:
        //  [1] {doc1 내용 ~200자 truncated}
        //  [2] {doc2 내용 ~200자 truncated}
        //  ..."

        String scoringPrompt = buildScoringPrompt(query, candidates);

        String response = chatClient.prompt()
            .user(scoringPrompt)
            .call()
            .content();

        // TODO: LLM 응답에서 점수 파싱
        List<Double> scores = parseScores(response, candidates.size());

        // TODO: 문서와 점수를 묶어 내림차순 정렬 후 상위 K개 반환
        return reorderByScores(candidates, scores, topK);
    }

    private String buildScoringPrompt(String query, List<Document> candidates) {
        // TODO: 점수화 프롬프트 구성.
        // 토큰 비용 절감을 위해 문서 내용을 ~200자로 truncate.
        throw new UnsupportedOperationException("구현해주세요");
    }

    private List<Double> parseScores(String response, int expectedCount) {
        // TODO: LLM 응답에서 double 배열 JSON 파싱.
        // 엣지 케이스 처리: 잘못된 형식의 JSON, 잘못된 수의 점수.
        // 파싱 실패 시 동일한 점수 목록 반환 (재순위화 없음).
        throw new UnsupportedOperationException("구현해주세요");
    }

    private List<Document> reorderByScores(List<Document> docs, List<Double> scores, int topK) {
        // TODO: 각 문서를 점수와 쌍으로, 점수 내림차순 정렬 후 상위 K개 반환.
        throw new UnsupportedOperationException("구현해주세요");
    }
}
```

## 핵심 설계 결정사항

- **1단계에서 몇 개의 후보를 검색할까요?** 20개로 시작하세요. 너무 적으면 관련 문서를 놓치고, 너무 많으면 재순위화기 프롬프트 비용이 비싸집니다.
- **LLM 재순위화 비용**: 각 재순위화 호출은 토큰을 소비합니다. 상위 20개 문서의 경우 재순위화 호출당 ~2,000-4,000 입력 토큰이 예상됩니다. 정확도 향상을 위해 허용 가능합니다.
- **배치 vs. 페어와이즈 점수화**: 모든 문서를 단일 프롬프트에서 점수화(배치)하는 것이 각 문서를 개별적으로 점수화(페어와이즈)하는 것보다 저렴합니다. 배치로 시작하세요.

## 다음 시도할 것

1. 간단한 점수화 프롬프트로 `LlmReRanker` 구현
2. `ReRankingRetrieval`에 연결 — 벡터 저장소에서 상위 20개 검색, 재순위화, 상위 K개 반환
3. 적용 전/후 Top-1 정확도 측정

LLM 기반 재순위화기가 충분히 정확하지 않으면 점수화 프롬프트를 조정하거나
전용 재순위화기 모델로 전환해보세요.

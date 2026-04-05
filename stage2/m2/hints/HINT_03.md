# 힌트 03: 쿼리 변환과 하이브리드 검색

메타데이터 필터와 ReRanker로도 해결 안 되는 케이스가 있습니다.
구어체 쿼리와 고유명사 검색 문제입니다.

## 쿼리 변환 (Query Rewriting)

"걍 환불해주세요 ㅠㅠ" → 영어 FAQ에서 매칭이 잘 안 됩니다.

```java
@Service
public class QueryTransformRetrieval implements RetrievalStrategy {

    @Override
    public List<Document> retrieve(String originalQuery) {
        String rewritten = rewriteQuery(originalQuery);
        log.debug("쿼리 변환: '{}' → '{}'", originalQuery, rewritten);

        return vectorStore.similaritySearch(
            SearchRequest.query(rewritten).withTopK(5)
        );
    }

    private String rewriteQuery(String query) {
        return chatClient.prompt()
            .system("""
                고객 질문을 영어 FAQ 검색에 최적화된 형태로 변환하세요.
                규칙:
                1. 구어체, 이모티콘 제거
                2. 핵심 의도만 남기기
                3. 영어 FAQ 키워드로 변환 (예: 환불→refund, 배송→shipping)
                4. 변환된 쿼리만 출력 (설명 없이)
                """)
            .user(query)
            .call()
            .content()
            .trim();
    }
}
```

## 하이브리드 검색 (벡터 + 키워드)

주문번호(`20240815-042`), 상품 코드, 고유명사는 벡터 유사도로 잘 안 잡힙니다.
BM25 키워드 검색과 결합하면 이런 케이스를 보완할 수 있습니다.

```java
public List<Document> hybridSearch(String query, int topK) {
    // 벡터 검색 결과
    List<Document> vectorResults = vectorStore.similaritySearch(
        SearchRequest.query(query).withTopK(topK * 2)
    );

    // BM25 키워드 검색 결과 (Qdrant sparse vector 또는 별도 구현)
    List<Document> keywordResults = bm25Search(query, topK * 2);

    // RRF(Reciprocal Rank Fusion)로 결합
    return reciprocalRankFusion(vectorResults, keywordResults, topK);
}

private List<Document> reciprocalRankFusion(
    List<Document> list1, List<Document> list2, int topK) {

    Map<String, Double> scores = new HashMap<>();
    int k = 60;  // RRF 상수

    // 각 리스트에서 순위 기반 점수 합산
    for (int i = 0; i < list1.size(); i++) {
        String id = list1.get(i).getId();
        scores.merge(id, 1.0 / (k + i + 1), Double::sum);
    }
    for (int i = 0; i < list2.size(); i++) {
        String id = list2.get(i).getId();
        scores.merge(id, 1.0 / (k + i + 1), Double::sum);
    }

    // 점수 기준 정렬 후 상위 K개 반환
    return scores.entrySet().stream()
        .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
        .limit(topK)
        .map(e -> findDocById(e.getKey(), list1, list2))
        .toList();
}
```

## 전략 조합

단일 전략보다 조합이 더 효과적일 수 있습니다:

```java
// 쿼리 변환 → 메타데이터 필터 → ReRanker
public List<Document> fullPipelineSearch(String question) {
    String rewritten = queryTransformer.rewrite(question);

    List<Document> candidates = vectorStore.similaritySearch(
        SearchRequest.query(rewritten)
            .withTopK(20)
            .withFilterExpression("status == 'current' OR layer == 'faq'")
    );

    return reranker.rerank(question, candidates, 5);
}
```

## 청크 사이즈 튜닝

검색 품질에 영향을 주는 또 다른 요소입니다.

| 청크 크기 | 장점 | 단점 |
|----------|------|------|
| 작음 (Q&A 단위) | 정밀한 검색, ReRanker 효과적 | 컨텍스트 분절 가능 |
| 큼 (섹션 단위) | 풍부한 컨텍스트 | 검색 정밀도 낮음, truncation 위험 |

현재 M1에서 선택한 청크 크기가 M2 검색 품질에 직접 영향을 미칩니다.
청크 크기를 바꿔서 정확도 변화를 측정해보세요.

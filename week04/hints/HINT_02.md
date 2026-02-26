# 힌트 02: 쿼리 변환 — 더 나은 질문하기

## 문제

사용자들은 나쁜 쿼리를 입력합니다. 정말로요.

| 사용자가 입력하는 것 | 실제로 의미하는 것 |
|---------------------|----------------------|
| "환불" | "환불 정책이 어떻게 되며 어떻게 요청하나요?" |
| "안 돼요" | "주문 상태가 배송 완료로 나오는데 아직 받지 못했습니다" |
| "주문 취소 배송 주소 결제" | 세 가지 질문을 하나로 합쳐놓음 |

벡터 검색은 명확하고 구체적인 쿼리에서 가장 잘 작동합니다. 쿼리가 모호하거나,
짧거나, 다면적이면 임베딩이 올바른 의미를 포착하지 못합니다.

**해결책**: 벡터 저장소에 보내기 전에 쿼리를 변환합니다.

## Spring AI 1.1.x — 표준 API 방식 (권장)

`spring-ai-rag` 모듈이 제공하는 표준 클래스를 사용합니다.

```java
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;

// 쿼리 재작성
RewriteQueryTransformer transformer = RewriteQueryTransformer.builder()
    .chatClientBuilder(chatClientBuilder)
    .build();
Query rewritten = transformer.transform(new Query(originalQuery));

// 멀티 쿼리 확장 (기본 3개)
MultiQueryExpander expander = MultiQueryExpander.builder()
    .chatClientBuilder(chatClientBuilder)
    .build();
List<Query> expanded = expander.expand(rewritten);
```

전체 파이프라인 예시:

```java
@Override
public List<Document> retrieve(String query, int topK) {
    // 1단계: 재작성
    Query rewritten = transformer.transform(new Query(query));

    // 2단계: 멀티 쿼리 확장
    List<Query> queries = expander.expand(rewritten);

    // 3단계: 각 쿼리로 검색 후 병합
    return queries.stream()
        .flatMap(q -> vectorStore.similaritySearch(
            SearchRequest.builder().query(q.text()).topK(topK).build()
        ).stream())
        .distinct()
        .limit(topK)
        .toList();
}
```

## ChatClient 직접 구현 방식 (1.0.x 호환)

`spring-ai-rag` 모듈 없이 `ChatClient`를 직접 사용해도 동일한 효과를 냅니다.
내부 동작을 이해하거나 커스터마이징이 필요할 때 유용합니다.

### 1. 쿼리 재작성

```java
private String rewriteQuery(String originalQuery) {
    String prompt = """
            다음 검색 쿼리를 더 명확하고 구체적으로 재작성해 주세요.
            재작성된 쿼리만 반환하고, 다른 설명은 포함하지 마세요.
            
            원본 쿼리: %s
            """.formatted(originalQuery);
    
    return chatClient.prompt()
            .user(prompt)
            .call()
            .content()
            .trim();
}
```

### 2. 멀티 쿼리 확장

```java
private List<String> expandQuery(String query, int count) {
    String prompt = """
            다음 질문에 대해 %d가지 다른 검색 쿼리를 생성해 주세요.
            각 쿼리는 질문의 다른 측면을 다뤄야 합니다.
            쿼리만 한 줄씩 반환하고, 번호나 다른 텍스트는 포함하지 마세요.
            
            질문: %s
            """.formatted(count, query);
    
    String response = chatClient.prompt()
            .user(prompt)
            .call()
            .content();
    
    return Arrays.stream(response.split("\n"))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .limit(count)
            .toList();
}
```

### 3. 전체 구현 흐름

```java
@Override
public List<Document> retrieve(String query, int topK) {
    // 1단계: 재작성
    String rewrittenQuery = rewriteQuery(query);
    
    // 2단계: 여러 쿼리로 확장
    List<String> expandedQueries = new ArrayList<>(expandQuery(rewrittenQuery, 3));
    expandedQueries.add(0, rewrittenQuery); // 재작성된 쿼리도 포함
    
    // 3단계: 각 쿼리로 검색
    List<List<Document>> allResults = new ArrayList<>();
    for (String q : expandedQueries) {
        List<Document> results = vectorStore.similaritySearch(
            SearchRequest.builder().query(q).topK(topK).build()
        );
        allResults.add(results);
    }
    
    // 4단계: 병합 및 중복 제거
    return mergeAndDeduplicate(allResults, topK);
}
```

## 언제 어떤 변환을 사용할까요?

| 상황 | 전략 | 이유 |
|-----------|----------|-----|
| 짧거나 모호한 쿼리 ("환불", "도움") | 쿼리 재작성 | 구체성 추가 |
| 복잡한 다면적 질문 | 멀티 쿼리 확장 | 다양한 측면 포괄 |
| 멀티 턴 대화 | 압축 (수동) | 대명사와 컨텍스트 해소 |

## 비용 고려사항

각 변환은 LLM 호출을 유발합니다. 3개 쿼리로 확장하면 검색 전에 이미 LLM 호출이 2번 발생합니다
(재작성 1번 + 확장 1번). 프로덕션에서는 정확도 향상이 가장 큰 변환 하나만 선택하는 것이 좋습니다.


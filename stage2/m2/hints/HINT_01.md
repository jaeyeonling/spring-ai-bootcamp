# 힌트 01: 메타데이터 필터 — 구버전과 오답을 걸러내기

검색 결과에 구버전 정책이나 상담사 오답이 섞여 나오는 문제입니다.
가장 단순하고 효과적인 첫 번째 전략입니다.

## Spring AI 메타데이터 필터

```java
// 현행 정책만 검색
SearchRequest request = SearchRequest.query(question)
    .withTopK(5)
    .withFilterExpression("status == 'current'");

List<Document> results = vectorStore.similaritySearch(request);
```

## 복합 필터

```java
// 현행 정책 + 정확한 상담사 답변만
SearchRequest request = SearchRequest.query(question)
    .withTopK(5)
    .withFilterExpression(
        "status == 'current' OR (layer == 'faq') OR (layer == 'chatlog' AND agent_accuracy == 'correct')"
    );
```

## 레이어별 가중치 전략

질문 유형에 따라 어떤 레이어를 우선할지 결정합니다:

```java
public List<Document> searchWithStrategy(String question) {
    // 1단계: FAQ에서 검색 (정답 앵커)
    List<Document> faqResults = vectorStore.similaritySearch(
        SearchRequest.query(question)
            .withTopK(3)
            .withFilterExpression("layer == 'faq'")
    );

    // 2단계: 현행 정책에서 검색 (상세 규정)
    List<Document> policyResults = vectorStore.similaritySearch(
        SearchRequest.query(question)
            .withTopK(3)
            .withFilterExpression("layer == 'policy' AND status == 'current'")
    );

    // 3단계: 상담 로그에서 유사 사례 검색 (선례)
    List<Document> chatResults = vectorStore.similaritySearch(
        SearchRequest.query(question)
            .withTopK(2)
            .withFilterExpression("layer == 'chatlog' AND agent_accuracy == 'correct'")
    );

    // 합쳐서 반환 (FAQ 우선)
    return Stream.of(faqResults, policyResults, chatResults)
        .flatMap(List::stream)
        .distinct()
        .limit(5)
        .toList();
}
```

## 적용 전후 측정

필터 적용 전후의 정확도를 반드시 비교하세요:

```
필터 없음:  전체 정확도 38% (Easy 62%, Medium 22%, Hard 12%)
status=current 필터:  전체 정확도 ?%
레이어별 분리 검색:   전체 정확도 ?%
```

어떤 필터가 얼마나 효과가 있는지 직접 측정하는 것이 목표입니다.

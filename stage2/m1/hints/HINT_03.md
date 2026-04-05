# 힌트 03: 메타데이터 설계와 버전 충돌 처리

데이터를 적재했는데 검색 결과에 구버전 정책이 섞여 나옵니다.
`deprecated/` 폴더의 v1, v2 문서 때문입니다.

## 메타데이터 설계

적재할 때 메타데이터를 잘 설계해야 나중에 필터링이 가능합니다.

```java
// Layer 2 문서 적재 시
Map<String, Object> metadata = new HashMap<>();
metadata.put("layer", "policy");
metadata.put("version", meta.get("version"));           // v1, v2, v3
metadata.put("status", meta.get("status"));             // current, deprecated
metadata.put("effective_date", meta.get("effective_date"));
metadata.put("category", meta.get("category"));         // returns, shipping, ...
metadata.put("source_file", filename);

Document doc = new Document(bodyContent, metadata);
```

## deprecated/ 처리 전략

두 가지 옵션이 있습니다:

**옵션 A: 아예 적재하지 않는다**
```java
// current/ 폴더만 읽기
Files.walk(policiesDir.resolve("current"))
    .filter(p -> p.toString().endsWith(".md"))
    .forEach(this::ingestPolicyDoc);
```
장점: 단순, 버전 충돌 없음
단점: 구버전 정책 학습 기회를 잃음 (M2에서 필터 기법 배울 수 없음)

**옵션 B: 메타데이터와 함께 모두 적재한다**
```java
// deprecated/ 포함하여 모두 읽되, status 메타데이터 기록
Map<String, String> meta = parseFrontmatter(content);
String status = path.toString().contains("deprecated") ? "deprecated" : meta.getOrDefault("status", "current");
metadata.put("status", status);
```
장점: M2에서 메타데이터 필터 효과를 실제로 경험 가능
단점: 필터 없이 검색하면 구버전이 섞임

**M2 학습을 위해 옵션 B를 권장합니다.**
필터 없이 검색했을 때 구버전이 섞이는 문제를 직접 경험하고,
M2에서 메타데이터 필터로 해결하는 흐름이 자연스럽습니다.

## Layer 3: 오답 상담 로그 처리

`agent_accuracy: wrong`인 상담 로그도 같은 고민이 필요합니다:

```java
metadata.put("agent_accuracy", chatLog.agent_accuracy());  // correct, wrong, partial
metadata.put("resolution", chatLog.resolution());           // resolved, escalated, unresolved
```

오답 로그를 포함하면 검색 노이즈가 증가합니다.
하지만 M2에서 `agent_accuracy=correct` 필터로 해결하는 경험을 할 수 있습니다.

## 적재 완료 후 검증

```java
// 레이어별 문서 수 확인
SearchRequest faqQuery = SearchRequest.query("test")
    .withFilterExpression("layer == 'faq'")
    .withTopK(1);

// Qdrant REST API로 직접 확인
// GET http://localhost:6333/collections/cholog-faq
// → "vectors_count": 3100 이상이면 성공
```

## 영속성 테스트

```java
@Test
void 서버_재시작_후_데이터_유지() {
    // 적재 후 검색
    List<Document> results = vectorStore.similaritySearch(
        SearchRequest.query("반품 정책").withTopK(3)
    );
    assertThat(results).hasSize(3);
    // 서버 재시작 시뮬레이션은 통합 테스트에서
}
```

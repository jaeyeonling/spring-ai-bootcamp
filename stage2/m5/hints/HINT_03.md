# 힌트 03: 시맨틱 캐싱

"배송 기간이 얼마나 되나요?"와 "배송 얼마나 걸려요?"는 같은 질문입니다.
두 번째 질문에 LLM을 다시 호출할 필요가 없습니다.

## 시맨틱 캐시 구조

```java
@Service
public class SemanticCache {

    private final VectorStore cacheStore;   // 캐시 전용 Qdrant 컬렉션
    private final EmbeddingModel embeddingModel;
    private static final double SIMILARITY_THRESHOLD = 0.95;

    public Optional<String> get(String question) {
        List<Document> similar = cacheStore.similaritySearch(
            SearchRequest.query(question)
                .withTopK(1)
                .withSimilarityThreshold(SIMILARITY_THRESHOLD)
        );

        if (!similar.isEmpty()) {
            log.debug("캐시 히트: '{}'", question);
            return Optional.of(similar.get(0).getMetadata().get("answer").toString());
        }
        return Optional.empty();
    }

    public void put(String question, String answer) {
        Document cacheEntry = new Document(
            question,  // 질문을 임베딩 텍스트로
            Map.of(
                "answer", answer,
                "cached_at", Instant.now().toString()
            )
        );
        cacheStore.add(List.of(cacheEntry));
        log.debug("캐시 저장: '{}'", question);
    }
}
```

## 서비스에 캐시 적용

```java
@Service
public class CachedRagService {

    private final SemanticCache cache;
    private final RagService ragService;
    private final MeterRegistry registry;

    public String ask(String question) {
        // 1. 캐시 확인
        Optional<String> cached = cache.get(question);
        if (cached.isPresent()) {
            registry.counter("cache.hit").increment();
            return cached.get();
        }

        // 2. 캐시 미스 → LLM 호출
        registry.counter("cache.miss").increment();
        String answer = ragService.ask(question);

        // 3. 결과 캐시 저장
        cache.put(question, answer);
        return answer;
    }
}
```

## 캐시 유효기간 관리

정책이 바뀌면 캐시된 오래된 답변이 문제가 됩니다:

```java
// 캐시 저장 시 TTL 메타데이터
Map.of(
    "answer", answer,
    "cached_at", Instant.now().toString(),
    "expires_at", Instant.now().plus(24, ChronoUnit.HOURS).toString()
)

// 조회 시 만료 확인
public Optional<String> get(String question) {
    List<Document> similar = cacheStore.similaritySearch(...);
    if (similar.isEmpty()) return Optional.empty();

    Document entry = similar.get(0);
    Instant expiresAt = Instant.parse(entry.getMetadata().get("expires_at").toString());
    if (Instant.now().isAfter(expiresAt)) {
        cacheStore.delete(List.of(entry.getId()));
        return Optional.empty();
    }
    return Optional.of(entry.getMetadata().get("answer").toString());
}
```

## 캐시 효과 측정

```java
// 캐시 히트율
double hitRate = cacheHits / (cacheHits + cacheMisses);

// 비용 절감
double savedCost = cacheHits * avgCostPerRequest;
```

일반적으로 FAQ 챗봇에서 반복 질문 비율은 30-50%입니다.
캐시 히트율이 낮다면 `SIMILARITY_THRESHOLD`를 낮춰보세요 (0.95 → 0.90).
단, 너무 낮추면 다른 질문에 잘못된 답을 반환합니다.

## 프로덕션 고려사항

개발 환경: 인메모리 캐시 (`ConcurrentHashMap`) 충분
프로덕션: Redis + 벡터 유사도 또는 전용 Qdrant 컬렉션

# 힌트 01: 왜 벡터 DB가 필요한가 — InMemory의 한계

Stage 1에서 `InMemoryVectorStore`(또는 직접 만든 유사도 검색)를 사용했을 것입니다.

## InMemory 방식의 문제

```java
// Stage 1에서 했던 방식
@PostConstruct
public void init() {
    List<String> chunks = loadAndSplit("layer1_faq/orders.md");
    chunks.forEach(chunk -> {
        float[] vector = embeddingModel.embed(chunk);
        store.add(chunk, vector);  // 메모리에 저장
    });
}
```

| 문제 | 설명 |
|------|------|
| **영속성 없음** | 서버 재시작하면 3,100건 임베딩을 다시 해야 함 (30분 + $) |
| **메모리 한계** | 3,100건 × 1536차원 float = 약 19MB. 100만 건이면 6.3GB |
| **선형 검색** | 모든 벡터를 순서대로 비교 — 3,100건은 빠르지만 100만 건은 느림 |
| **메타데이터 필터 없음** | "status=current인 문서만" 같은 조건 검색 불가 |

## Qdrant가 해결하는 것

```
InMemoryVectorStore     →     Qdrant
─────────────────────────────────────
Map<String, float[]>    →     영속 스토리지 (Docker volume)
선형 탐색 O(n)          →     HNSW 인덱스 O(log n)
메타데이터 없음         →     payload 필드 + 필터 검색
서버 재시작 시 초기화   →     데이터 유지
```

## HNSW 인덱스 (간단히)

백엔드 개발자에게 익숙한 비유: Qdrant의 HNSW는 데이터베이스의 B-Tree 인덱스와 같습니다.
- B-Tree: 텍스트/숫자 값으로 빠른 검색
- HNSW: 벡터 유사도로 빠른 검색

직접 구현하지 않아도 됩니다. Qdrant가 자동으로 관리합니다.

## 다음 단계

Qdrant Docker를 실행하고 Spring AI 연결이 되면 HINT_02로 넘어가세요.

```bash
docker run -p 6333:6333 -p 6334:6334 \
  -v $(pwd)/qdrant_storage:/qdrant/storage \
  qdrant/qdrant:v1.9.0
```

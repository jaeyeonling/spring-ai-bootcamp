# ANN과 HNSW (Approximate Nearest Neighbor)

> 수백만 벡터 중에서 가장 가까운 이웃을 **정확하게** 찾는 대신, **거의 정확하게** 찾되 훨씬 빠르게 찾는 검색 알고리즘.

## 왜 필요한가

[Week 01](../../week01/notes/NOTES.md)에서 구현한 `InMemoryVectorStore`의 검색:

```java
// 브루트포스: 모든 벡터와 코사인 유사도 계산
for (EmbeddedChunk chunk : store) {
    double similarity = cosineSimilarity(queryVector, chunk.vector());
    // ...
}
```

이 방식은 **O(n)** — 청크가 n개이면 n번 비교해야 한다.

| 청크 수 | 브루트포스 | ANN (HNSW) |
|:-------:|:---------:|:----------:|
| 100 | ~1ms | ~1ms |
| 1,000 | ~10ms | ~2ms |
| 100,000 | ~1s | ~5ms |
| 1,000,000 | ~10s | ~5ms |
| 10,000,000 | ~100s | ~10ms |

100만 벡터에서 10초 vs 5밀리초. 프로덕션에서 브루트포스는 선택지가 아니다.

> **"Approximate"의 의미**: ANN은 진짜 1위가 아닌 벡터를 1위로 반환할 수 있다. 하지만 recall@10 (상위 10개 중 진짜 top-10이 포함된 비율)이 95-99%이므로, RAG에서는 실질적으로 문제가 되지 않는다. top-5를 요청했는데 진짜 3위 대신 6위가 들어와도, 어차피 LLM은 5개 컨텍스트를 종합해서 답하므로 최종 답변 품질에 영향이 거의 없다.

## ANN 알고리즘 종류

| 알고리즘 | 핵심 아이디어 | 장점 | 단점 |
|---------|-------------|------|------|
| **HNSW** | 계층적 그래프 탐색 | 검색 빠름, recall 높음 | 메모리 사용량 큼 |
| IVF | 벡터를 클러스터로 분류 후 해당 클러스터만 검색 | 메모리 효율적 | 클러스터 경계에서 recall 하락 |
| PQ (Product Quantization) | 벡터를 압축하여 저장 | 메모리 매우 효율 | 정밀도 손실 |
| HNSW + PQ | HNSW로 후보군, PQ로 재순위화 | 대규모에 유리 | 구현 복잡 |

**Qdrant, Pinecone, Weaviate** 등 주요 벡터 DB는 모두 HNSW를 기본 인덱스로 사용한다.

## HNSW (Hierarchical Navigable Small World) 동작 원리

### 핵심 직관: "친구의 친구"로 탐색

1000만 명의 사람 중에서 "서울에 사는 AI 엔지니어"를 찾는다고 상상.

- **브루트포스**: 1000만 명 전부에게 물어보기 → 느림
- **HNSW 방식**: "AI 관련 일 하는 사람 아는?" → "서울에 있는 AI 엔지니어 아는?" → 2-3 홉이면 도달

### 구조: 다층 그래프

```
Layer 3 (최상위, 노드 극소수):    A ─────────── B
                                  │
Layer 2:                    A ── C ── D ─── B
                            │         │
Layer 1:                A ─ E ─ C ─ F ─ D ─ G ─ B
                        │   │   │   │   │   │   │
Layer 0 (최하위, 모든 노드): A E H C I F J D K G L B
```

- **Layer 0**: 모든 벡터가 존재. 가까운 벡터끼리 엣지로 연결
- **Layer 1+**: 일부 벡터만 존재 ("고속도로 진입로"). 층이 올라갈수록 노드가 줄고 엣지가 길어짐
- 각 노드가 어느 레이어까지 올라가는지는 삽입 시 랜덤으로 결정 (Skip List와 같은 확률 구조)

### 검색 과정

```
쿼리 벡터 Q가 들어오면:

1. Layer 3(최상위)에서 시작
   → A와 B 중 Q에 더 가까운 쪽으로 이동 (예: A)

2. Layer 2로 내려감
   → A 주변의 이웃(C, D) 중 Q에 더 가까운 쪽으로 그리디 탐색
   → C로 이동

3. Layer 1로 내려감
   → C 주변(E, F, ...) 탐색, 더 가까운 F로 이동

4. Layer 0에서 최종 탐색
   → F 주변의 실제 이웃들을 탐색하여 top-K 반환
```

**시간 복잡도**: O(log n) — 각 레이어에서 그리디 탐색이 O(1)이고, 레이어 수가 O(log n).

### 삽입 과정

```
새 벡터 V를 삽입:

1. 랜덤으로 최대 레이어 결정 (예: Layer 2)
   → 확률적으로 상위 레이어에 배치될수록 "고속도로 진입로" 역할

2. 검색과 동일한 방식으로 V의 위치 찾기

3. Layer 0 ~ Layer 2에서 V와 가장 가까운 M개의 노드에 엣지 연결
   → M (max connections): HNSW의 핵심 파라미터
```

### HNSW 파라미터

| 파라미터 | 의미 | 기본값 (Qdrant) | 효과 |
|---------|------|:----------:|------|
| `M` | 각 노드의 최대 연결 수 | 16 | 크면: recall ↑, 메모리 ↑, 삽입 느림 |
| `ef_construct` | 삽입 시 탐색 범위 | 100 | 크면: 인덱스 품질 ↑, 삽입 느림 |
| `ef` | 검색 시 탐색 범위 | 128 | 크면: recall ↑, 검색 느림 |

> **프로덕션 튜닝**: 대부분의 경우 기본값으로 충분하다. recall이 부족하면 `ef`를 올리고, 메모리가 부족하면 `M`을 줄인다. Week 03에서는 Qdrant 기본값을 그대로 사용.

## 벡터 DB에서의 ANN

Week 03에서 `vectorStore.add(chunks)` 한 줄로 HNSW 인덱싱이 자동 수행:

```yaml
# application.yml
spring.ai.vectorstore.qdrant:
  host: localhost
  port: 6334
  collection-name: week03-faq
  initialize-schema: true   # 컬렉션 + HNSW 인덱스 자동 생성
```

```java
// 내부적으로 일어나는 일:
vectorStore.add(chunks);
// 1. EmbeddingModel.embed() → float[1536] per chunk
// 2. Qdrant에 벡터 + 메타데이터 upsert
// 3. HNSW 인덱스에 새 노드 삽입 + 엣지 연결

vectorStore.similaritySearch(SearchRequest.builder().query(q).topK(5).build());
// 1. EmbeddingModel.embed(q) → float[1536]
// 2. Qdrant HNSW에서 ANN 검색 → top-5
// 3. 벡터 + 메타데이터 + 텍스트 반환
```

Spring AI의 `VectorStore` 인터페이스가 이 모든 것을 추상화하므로, HNSW 파라미터를 직접 다룰 일은 거의 없다. 하지만 검색 결과가 이상하거나 성능이 부족할 때, "이것은 ANN이므로 100% 정확하지 않다"는 사실을 아는 것이 디버깅의 출발점이다.

## 브루트포스 vs ANN: 언제 어떤 것을?

| | 브루트포스 | ANN (HNSW) |
|-|-----------|------------|
| 정확도 | 100% (exact) | ~95-99% (approximate) |
| 속도 | O(n) | O(log n) |
| 메모리 | 벡터만 | 벡터 + 인덱스 (1.5-2x) |
| 적합한 규모 | < 10,000 벡터 | 10,000+ 벡터 |
| 구현 | for 루프 | 벡터 DB 필요 |

> Week 01-02에서 24개 FAQ 청크로는 브루트포스가 충분했다. 하지만 "문서가 1,000페이지로 늘었다"는 Week 03의 시나리오에서는 HNSW가 필수.

## 학습 자료

### Level 0 (직관 잡기)

- **Pinecone — What is a Vector Database?**: https://www.pinecone.io/learn/vector-database/

### Level 1 (HNSW 이해)

- **James Briggs — HNSW Explained**: https://www.youtube.com/watch?v=QvKMwLjdK-s
- **Qdrant — HNSW**: https://qdrant.tech/documentation/concepts/indexing/#vector-index

### Level 2 (논문)

- **Malkov & Yashunin (2018) — Efficient and robust approximate nearest neighbor using Hierarchical Navigable Small World graphs**: https://arxiv.org/abs/1603.09320

## 등장한 주차

- [Week 01 구현 노트](../../week01/notes/NOTES.md) — 브루트포스 검색 (O(n)). HNSW가 필요한 이유의 출발점
- [Week 03 구현 노트](NOTES.md) — Qdrant HNSW 도입. `vectorStore.similaritySearch()` 내부에서 동작

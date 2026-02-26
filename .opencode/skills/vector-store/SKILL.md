---
name: vector-store
description: 벡터 저장소 개념, 인메모리 구현, Qdrant 사용 가이드
---

# Vector Store Guide

## 벡터 저장소란?

텍스트의 의미를 벡터로 저장하고, **의미적 유사도**로 검색할 수 있는 특수 데이터베이스입니다.

```
기존 DB:   SELECT * FROM faq WHERE content LIKE '%환불%'  → 키워드 매칭
벡터 DB:   "돈을 돌려받고 싶어요" → 의미가 유사한 "환불 정책" 검색  → 의미 매칭
```

## 인메모리 벡터 저장소 (Week 01-02)

### 직접 구현

```java
public class InMemoryVectorStore {
    
    private final List<EmbeddedChunk> chunks = new ArrayList<>();
    
    public record EmbeddedChunk(String text, float[] vector) {}
    
    public void add(String text, float[] vector) {
        chunks.add(new EmbeddedChunk(text, vector));
    }
    
    public List<String> search(float[] queryVector, int topK) {
        return chunks.stream()
            .map(chunk -> Map.entry(chunk, cosineSimilarity(queryVector, chunk.vector())))
            .sorted(Map.Entry.<EmbeddedChunk, Double>comparingByValue().reversed())
            .limit(topK)
            .map(entry -> entry.getKey().text())
            .toList();
    }
    
    public static double cosineSimilarity(float[] a, float[] b) {
        double dotProduct = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
```

### 한계

- 서버 재시작 시 데이터 손실
- 대량 데이터에서 느린 선형 검색 O(n)
- 메모리 제한

## Qdrant 벡터 DB (Week 03+)

### 왜 Qdrant인가?

| 인메모리 | Qdrant |
|---------|--------|
| 재시작 시 손실 | 영구 저장 |
| O(n) 선형 검색 | HNSW 인덱스 (O(log n)) |
| 메모리 제한 | 디스크 기반 확장 가능 |
| 단일 프로세스 | 분산 클러스터 가능 |

### Docker로 실행

```bash
docker run -d -p 6333:6333 -p 6334:6334 qdrant/qdrant
```

### Spring AI 연동

```yaml
# application.yml
spring:
  ai:
    vectorstore:
      qdrant:
        host: localhost
        port: 6334
        collection-name: faq-embeddings
```

```java
@Autowired
QdrantVectorStore vectorStore;

// 문서 저장
vectorStore.add(documents);

// 유사도 검색
List<Document> results = vectorStore.similaritySearch(
    SearchRequest.builder()
        .query("환불 방법 알려주세요")
        .topK(5)
        .build()
);
```

## ETL 파이프라인 (Week 03)

문서를 벡터 DB에 적재하는 파이프라인입니다.

```
[문서 로드]  →  [텍스트 분할]  →  [임베딩 생성]  →  [벡터 DB 저장]
  Extract         Transform          Transform          Load
```

### Spring AI ETL

```java
// 1. Extract: 문서 로드
TikaDocumentReader reader = new TikaDocumentReader(resource);
List<Document> documents = reader.get();

// 2. Transform: 텍스트 분할
TokenTextSplitter splitter = new TokenTextSplitter();
List<Document> chunks = splitter.apply(documents);

// 3. Load: 벡터 DB 저장 (임베딩 자동 생성)
vectorStore.add(chunks);
```

## 고급 검색 (Week 04)

### 쿼리 변환

원래 질문을 검색에 최적화된 형태로 변환합니다.

```java
// "환불받으려면 어떻게 해야 하나요?" 
// → "return refund policy procedure"
String transformedQuery = chatClient.prompt()
    .system("질문을 검색에 최적화된 키워드로 변환하세요")
    .user(originalQuery)
    .call()
    .content();
```

### ReRanker

초기 검색 결과를 더 정밀하게 재정렬합니다.

```
초기 검색 (top-20)  →  ReRanker  →  최종 결과 (top-5)
   빠르지만 거친        느리지만 정밀     고품질 결과
```

### 하이브리드 검색

벡터 검색과 키워드 검색을 결합합니다.

```
질문 → [벡터 검색] → 결과 A
     → [키워드 검색] → 결과 B
     → [결과 병합 & 정렬] → 최종 결과
```

## 관련 스킬

- `rag-pipeline`: RAG 전체 흐름
- `spring-ai-api`: Spring AI VectorStore API

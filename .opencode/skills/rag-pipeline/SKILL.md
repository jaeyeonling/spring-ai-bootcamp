---
name: rag-pipeline
description: RAG (Retrieval-Augmented Generation) 파이프라인 개념, 구현, 최적화 가이드
---

# RAG Pipeline Guide

## RAG란?

**RAG (Retrieval-Augmented Generation)**는 LLM이 답변하기 전에 관련 문서를 검색하여
컨텍스트로 제공하는 패턴입니다. "오픈북 시험"과 같습니다.

```
사용자 질문
    |
    v
[1. 질문 임베딩]           ← Embedding
    |
    v
[2. 유사 문서 검색]        ← Retrieval
    |
    v
[3. 컨텍스트로 프롬프트 구성] ← Augmentation
    |
    v
[4. LLM으로 답변 생성]     ← Generation
    |
    v
답변 + 토큰 사용량
```

## 왜 RAG가 필요한가?

### "그냥 프롬프트에 다 넣기"의 한계

```java
// 작동하지만 문제가 있는 방식
String faq = Files.readString(Path.of("faq.md"));
chatClient.prompt()
    .system("FAQ:\n" + faq)
    .user(question)
    .call();
```

| 문제 | 설명 |
|------|------|
| 토큰 비용 | 매 요청마다 전체 FAQ를 보냄 (수천~수만 토큰) |
| 컨텍스트 제한 | LLM의 컨텍스트 창 크기 한계 |
| 관련성 | 전체 문서 중 답변에 필요한 부분은 극소수 |
| 확장성 | 문서가 100페이지가 되면? |

### RAG의 해결

**필요한 부분만 검색해서 프롬프트에 넣기**

- 비용 절감: 관련 청크만 전송 (수백 토큰)
- 정확도 향상: 관련 컨텍스트만 제공하여 집중
- 확장 가능: 문서 크기에 상관없이 동작

## 임베딩 (Embedding)

텍스트를 의미를 담은 고차원 벡터로 변환합니다.

```java
// Spring AI EmbeddingModel 사용
EmbeddingModel embeddingModel; // 자동 주입
float[] vector = embeddingModel.embed("환불은 어떻게 받나요?");
// vector = [0.023, -0.041, 0.078, ...] — 1536차원 (text-embedding-3-small)
```

### 핵심 특성

- **의미 보존**: "환불 방법" ≈ "돈 돌려받기" (유사한 벡터)
- **교차 언어**: 한국어 질문 ≈ 영어 FAQ (다국어 모델)
- **고정 차원**: text-embedding-3-small → 1536차원

## 코사인 유사도 (Cosine Similarity)

두 벡터가 같은 방향을 가리키는 정도를 측정합니다.

```
similarity = dot(a, b) / (magnitude(a) * magnitude(b))
```

- `1.0` = 동일한 의미
- `0.0` = 무관
- `-1.0` = 반대 의미

### Java 구현

```java
public static double cosineSimilarity(float[] a, float[] b) {
    double dotProduct = 0.0;
    double normA = 0.0;
    double normB = 0.0;
    for (int i = 0; i < a.length; i++) {
        dotProduct += a[i] * b[i];
        normA += a[i] * a[i];
        normB += b[i] * b[i];
    }
    return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
}
```

## 청킹 (Chunking)

문서를 검색 가능한 단위로 분할합니다.

### 전략 1: 마크다운 헤더 기반 (Week 01)

```java
// "###"로 시작하는 각 Q&A를 하나의 청크로
Arrays.stream(content.split("(?=### )"))
    .map(String::trim)
    .filter(s -> !s.isBlank())
    .toList();
```

### 전략 2: 토큰 윈도우 (Week 02)

```java
// Spring AI의 TokenTextSplitter 사용
TokenTextSplitter splitter = new TokenTextSplitter(
    800,    // defaultChunkSize
    350,    // minChunkSizeChars
    200,    // minChunkLengthToEmbed
    100,    // maxNumChunks
    true    // keepSeparator
);
List<Document> chunks = splitter.split(documents);
```

### 청크 크기 트레이드오프

| 작은 청크 | 큰 청크 |
|-----------|---------|
| 정밀한 검색 | 넓은 컨텍스트 |
| 컨텍스트 부족 가능 | 노이즈 포함 가능 |
| 많은 청크 수 | 적은 청크 수 |

## 검색 (Retrieval)

### 기본 검색 (Week 01-02: 인메모리)

```java
public List<String> search(float[] queryVector, int topK) {
    return chunks.stream()
        .map(chunk -> Map.entry(chunk, cosineSimilarity(queryVector, chunk.vector())))
        .sorted(Map.Entry.<Chunk, Double>comparingByValue().reversed())
        .limit(topK)
        .map(entry -> entry.getKey().text())
        .toList();
}
```

### 벡터 DB 검색 (Week 03+: Qdrant)

```java
// Spring AI VectorStore 추상화
List<Document> results = vectorStore.similaritySearch(
    SearchRequest.builder()
        .query(question)
        .topK(5)
        .build()
);
```

### 고급 검색 (Week 04)

- **쿼리 변환**: 원래 질문을 검색에 최적화된 형태로 변환
- **ReRanker**: 초기 검색 결과를 재정렬하여 관련성 향상
- **하이브리드 검색**: 벡터 검색 + 키워드 검색 결합

## 증강 (Augmentation)

검색된 청크를 시스템 프롬프트에 주입합니다.

```java
String context = String.join("\n\n", relevantChunks);

chatClient.prompt()
    .system("""
        당신은 초록 코퍼레이션의 고객지원 담당자입니다.
        아래의 컨텍스트에 기반해서만 질문에 답하세요.
        컨텍스트에 답이 없으면 "해당 정보를 찾을 수 없습니다."라고 말하세요.

        컨텍스트:
        %s
        """.formatted(context))
    .user(question)
    .call();
```

## 생성 (Generation)

```java
ChatResponse response = chatClient.prompt()
    .system(systemPrompt)
    .user(question)
    .call()
    .chatResponse();

String answer = response.getResult().getOutput().getText();
Usage usage = response.getMetadata().getUsage();
```

## 백엔드 개발자를 위한 비유

| RAG 개념 | 백엔드 동등 개념 |
|---------|-----------------|
| 임베딩 | DB 인덱스 (검색을 빠르게) |
| 벡터 유사도 | WHERE + ORDER BY relevance |
| 청크 | 테이블의 행 |
| 인메모리 저장소 | HashMap 캐시 |
| Qdrant | Elasticsearch (의미 검색 전용) |
| 컨텍스트 창 | HTTP 요청 본문 크기 제한 |

## 관련 스킬

- `vector-store`: 벡터 저장소 상세
- `prompt-engineering`: 프롬프트 최적화
- `spring-ai-api`: Spring AI API 상세

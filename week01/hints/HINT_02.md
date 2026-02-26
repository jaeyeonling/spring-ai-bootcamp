# 힌트 02: 임베딩과 코사인 유사도

텍스트를 벡터로 변환할 수 있다는 것을 알았습니다. 그런데 검색에 어떻게 활용할까요?

## 핵심 아이디어

1. FAQ를 청크로 **분할**합니다 (예: 각 Q&A 쌍을 하나의 청크로)
2. 각 청크를 벡터로 **임베딩**합니다
3. 질문이 들어오면 **질문도 임베딩**합니다
4. 질문 벡터를 모든 청크 벡터와 **비교**합니다
5. **코사인 유사도**가 가장 높은 청크를 반환합니다

## 30초 코사인 유사도

두 벡터가 같은 방향을 가리키면 유사합니다.

```
similarity = dot(a, b) / (magnitude(a) * magnitude(b))
```

- `1.0` = 완전히 같은 방향 (동일한 의미)
- `0.0` = 전혀 관련 없음
- `-1.0` = 반대 의미

## Java 구현

Spring AI가 제공하는 `EmbeddingModel` 사용법:

```java
@Service
public class SimpleVectorSearch {

    private final EmbeddingModel embeddingModel;

    // 단일 텍스트 임베딩
    public float[] embed(String text) {
        return embeddingModel.embed(text);
    }

    // 두 벡터 사이의 코사인 유사도
    public double cosineSimilarity(float[] a, float[] b) {
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
}
```

## 상위 K개 유사 청크 찾기

```java
public List<String> search(String query, List<DocumentChunk> chunks, int topK) {
    float[] queryVector = embed(query);

    return chunks.stream()
        .map(chunk -> Map.entry(chunk, cosineSimilarity(queryVector, chunk.getVector())))
        .sorted(Map.Entry.<DocumentChunk, Double>comparingByValue().reversed())
        .limit(topK)
        .map(entry -> entry.getKey().getText())
        .toList();
}
```

## 아직 부족한 것

검색 부분은 완성되었습니다. 이제 필요한 것:
1. FAQ를 청크로 **분할**하는 방법
2. 시작할 때 **모든 청크를 임베딩**하고 메모리에 저장하는 방법
3. 검색 결과를 LLM 호출과 **결합**하는 방법

그게 힌트 03입니다.

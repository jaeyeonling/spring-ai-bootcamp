# 힌트 01: 벡터 데이터베이스가 필요한 이유

1주차/2주차 방식에서는 임베딩을 JVM 메모리의 `List<EmbeddedChunk>`에 저장했습니다.
10-50개 청크에는 잘 작동했습니다. 1,000개 이상에서 왜 문제가 되는지 알아봅시다.

## 문제 1: O(n) 검색

검색 루프가 이렇게 생겼습니다:

```java
for (EmbeddedChunk chunk : allChunks) {
    double score = cosineSimilarity(queryVector, chunk.vector());
    // ...
}
```

모든 쿼리가 **모든** 청크를 스캔합니다. 선형 시간입니다.

| 청크 수 | 검색 시간 (브루트 포스) |
|--------|--------------------------|
| 100    | ~5 ms                    |
| 1,000  | ~50 ms                   |
| 10,000 | ~500 ms                  |
| 1M     | ~50초                    |

실제 애플리케이션은 수백만 개의 벡터를 가집니다. 준선형 검색이 필요합니다.

## 문제 2: 영속성 없음

```java
private final List<EmbeddedChunk> chunks = new ArrayList<>();
```

이것은 JVM 힙 메모리에 존재합니다. 프로세스가 종료되면 사라집니다.
1,000페이지를 다시 임베딩하면 시간과 비용이 듭니다:

- 1,000페이지 ~ 50만 토큰 ~ text-embedding-3-small 기준 약 $0.01
- 하지만 대용량 데이터셋의 경우 API 호출에 30-60분 소요
- 재임베딩이 완료될 때까지 사용자는 결과를 받지 못함

## Qdrant란?

Qdrant는 목적에 특화된 벡터 데이터베이스입니다. 두 문제를 모두 해결합니다:

- **ANN (근사 최근접 이웃) 검색**: HNSW 인덱스를 사용해 O(n)이 아닌 O(log n) 시간에 유사한 벡터를 찾습니다. 100만 개 벡터 쿼리가 ~1-5ms 이내에 반환됩니다.
- **영속 저장소**: 벡터를 디스크에 씁니다. 프로세스를 재시작해도 데이터가 그대로입니다.
- **필터링**: 메타데이터(파일명, 페이지 번호, 날짜)를 첨부하고 검색 시 필터링할 수 있습니다.

## Docker로 Qdrant 실행

```bash
docker run -p 6333:6333 -p 6334:6334 qdrant/qdrant
```

- 포트 6333: REST API (http://localhost:6333/dashboard 에서 확인)
- 포트 6334: gRPC (Spring AI가 성능을 위해 사용)

실행 확인:

```bash
curl http://localhost:6333/healthz
# 반환: ok
```

대시보드 확인: http://localhost:6333/dashboard

## Spring AI + Qdrant 자동 설정

Spring AI는 `QdrantVectorStore` 빈을 자동 설정하는 스타터를 가지고 있습니다.
`build.gradle`에 이미 의존성이 있습니다:

```groovy
implementation 'org.springframework.ai:spring-ai-starter-vector-store-qdrant'
```

`application.yml`에서 설정:

```yaml
spring:
  ai:
    vectorstore:
      qdrant:
        host: localhost
        port: 6334
        collection-name: week03-faq
```

이제 어디서나 `VectorStore`를 주입할 수 있습니다:

```java
@Service
public class MyService {

    private final VectorStore vectorStore;

    public MyService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public void store(List<Document> documents) {
        vectorStore.add(documents);  // 임베딩 + Qdrant에 저장
    }

    public List<Document> search(String query) {
        return vectorStore.similaritySearch(query);  // ANN 검색
    }
}
```

주의: Qdrant 관련 클래스를 임포트하지 않습니다. `VectorStore`는 Spring AI 인터페이스입니다.
나중에 Qdrant를 Pinecone이나 Weaviate로 교체해도 코드는 변경되지 않습니다.

## 다음 할 일

저장하고 검색하는 방법을 알았습니다. 하지만 문서를 어떻게 **벡터 저장소에 넣을**까요?
PDF 읽기, 청크로 분할, 피드하기 — 그것이 ETL 파이프라인입니다.

힌트 02를 참고하세요.

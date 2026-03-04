# Week 03 — 벡터 DB와 ETL 구현 노트

> **미션**: FAQ 문서가 1,000페이지로 늘었다. InMemoryVectorStore의 두 가지 벽 — O(n) 검색 속도, 서버 재시작 시 데이터 소실 — 을 Qdrant 벡터 DB로 해결.  
> **핵심 전환**: 직접 구현에서 추상화 활용으로. `VectorStore` 인터페이스 하나로 백엔드 교체.

## 관련 개념

- RAG (Retrieval-Augmented Generation)
- [ANN과 HNSW (Approximate Nearest Neighbor)](CONCEPT_ANN_HNSW.md)
- 임베딩 (Embedding)
- 청킹 (Chunking)

---

## 구현 파일 구조

```
week03/src/main/java/com/jaeyeonling/week03/
├── Week03Application.java  — Spring Boot 진입점 (Bean 자동설정에 의존)
├── IngestController.java   — POST /api/ingest (멀티파트 파일 업로드)
├── IngestionService.java   — ETL 파이프라인: Read → Transform → Write
└── QueryController.java    — POST /api/query (RAG 질의 + 토큰 사용량)

week03/
├── docker-compose.yml      — Qdrant + 앱 원커맨드 실행
├── src/main/resources/application.yml
└── build.gradle
```

---

## 구현 흐름

```
[인제스트]
  POST /api/ingest (multipart file: PDF, DOCX, TXT, ...)
    ↓
  IngestController → IngestionService.ingest(file)
    ↓
  Step 1: TikaDocumentReader(ByteArrayResource) → List<Document>     [Read]
  Step 2: TokenTextSplitter().apply(documents) → List<Document>      [Transform]
  Step 3: chunk.getMetadata().put("source", filename)                [Enrich]
  Step 4: vectorStore.add(chunks)                                    [Write]
          → EmbeddingModel.embed() + Qdrant upsert
    ↓
  { filename, chunks, message }

[질의]
  POST /api/query { "question": "..." }
    ↓
  QueryController
    ↓
  vectorStore.similaritySearch(query, topK=5)  → ANN 검색 (HNSW)
    ↓
  context = sources.map(Document::getText).join("\n\n")
    ↓
  chatClient.prompt()
    .system("컨텍스트만 사용... " + context)
    .user(question)
    .call().chatResponse()
    ↓
  { answer, sources[], tokenUsage { promptTokens, completionTokens, totalTokens } }
```

---

## 핵심 구현 포인트

### ETL 파이프라인 — Read → Transform → Write

Week 01-02에서 `FaqLoader.loadDocument()` → `ChunkingStrategy.split()` → `InMemoryVectorStore.addAll()`로 수동 연결하던 것을, Spring AI의 ETL 구성 요소로 교체:

```java
// Read: 파일 형식 자동 감지 (Apache Tika 기반)
Resource resource = new ByteArrayResource(file.getBytes()) {
    @Override
    public String getFilename() {
        return file.getOriginalFilename();
    }
};
List<Document> rawDocuments = new TikaDocumentReader(resource).get();

// Transform: 토큰 기반 청크 분할
TokenTextSplitter splitter = new TokenTextSplitter();
List<Document> chunks = splitter.apply(rawDocuments);

// Enrich: 출처 메타데이터 추가
for (Document chunk : chunks) {
    chunk.getMetadata().put("source", file.getOriginalFilename());
}

// Write: 임베딩 + Qdrant 저장 (한 줄)
vectorStore.add(chunks);
```

**`ByteArrayResource` 오버라이드가 필요한 이유**: `TikaDocumentReader`는 파일 확장자로 파서를 선택한다. `ByteArrayResource`의 기본 `getFilename()`은 null을 반환하므로, 오버라이드하지 않으면 Tika가 적절한 파서를 선택하지 못한다.

**`vectorStore.add(chunks)` 한 줄에 일어나는 일**:

1. `EmbeddingModel.embed()`가 각 청크 텍스트를 `float[]`로 변환 (OpenAI API 호출)
2. 벡터 + 텍스트 + 메타데이터를 Qdrant에 upsert
3. Qdrant가 HNSW 인덱스를 업데이트하여 ANN 검색 준비 완료

Week 01에서 직접 구현한 `InMemoryVectorStore.addAll()`과 비교하면, 임베딩 계산은 동일하지만 **저장소가 JVM 힙 → 외부 DB**로 바뀌면서 영속성과 검색 성능이 동시에 해결된다.

### TikaDocumentReader — 1000+ 포맷 지원

```groovy
implementation 'org.springframework.ai:spring-ai-tika-document-reader'
```

Apache Tika가 지원하는 포맷: PDF, DOCX, PPTX, HTML, TXT, RTF, EPUB, CSV, XML, 이미지(OCR), 이메일, 압축 파일 등 1000+.

Week 01-02에서는 `Files.readString()`으로 마크다운만 읽었다. Week 03부터는 사용자가 어떤 형식의 파일을 업로드해도 텍스트를 추출할 수 있다.

> **학습자가 주의할 점**: Tika는 **텍스트 추출**만 한다. PDF 내부의 표, 이미지 내 텍스트(OCR), 수식 등은 완벽하게 추출되지 않을 수 있다. 프로덕션에서는 문서 유형별로 전처리 파이프라인을 분리하는 경우가 많다.

### VectorStore 추상화 — 백엔드 교체가 설정 한 줄

```java
// QueryController — Qdrant를 직접 참조하는 곳이 없다
private final VectorStore vectorStore;

List<Document> sources = vectorStore.similaritySearch(
    SearchRequest.builder().query(question).topK(5).build());
```

`VectorStore`는 Spring AI의 인터페이스. `application.yml`에서 Qdrant 설정을 Pinecone이나 Weaviate로 바꾸고 의존성만 교체하면 Java 코드 변경 없이 벡터 DB를 교체할 수 있다.

이것이 Week 01에서 `InMemoryVectorStore`를 **직접** 구현해본 것의 가치: 추상화 뒤에서 무슨 일이 일어나는지 아는 상태에서 추상화를 사용하면, 문제가 생겼을 때 "VectorStore.add()가 느린데 왜지?" → "임베딩 API 호출이 병목인가, Qdrant upsert가 병목인가?"로 디버깅 방향을 잡을 수 있다.

### Qdrant 설정 — gRPC 포트와 자동 스키마

```yaml
spring.ai.vectorstore.qdrant:
  host: localhost
  port: 6334                   # gRPC (REST는 6333)
  collection-name: week03-faq
  initialize-schema: true      # 컬렉션이 없으면 자동 생성
```

- **포트 6334 (gRPC)**: REST(6333)보다 직렬화/역직렬화 오버헤드가 적다. 대량 벡터 전송 시 유의미한 차이.
- **`initialize-schema: true`**: 첫 실행 시 `week03-faq` 컬렉션을 자동 생성. 수동으로 Qdrant API를 호출할 필요 없음. 벡터 차원은 `EmbeddingModel`의 출력 차원(text-embedding-3-small: 1536)에서 자동 감지.

### 시스템 프롬프트 — Week 02 대비 간소화

```java
"다음 컨텍스트만을 사용하여 질문에 답하세요. " +
"컨텍스트에 답이 없으면 '해당 내용은 FAQ에서 찾을 수 없습니다'라고 답하세요. " +
"질문과 동일한 언어로 답변하세요.\n\n" + context
```

Week 02의 Experiment 6 프롬프트에서 Few-shot과 CoT가 빠졌다. 이유: Week 03의 초점은 **인프라 교체**(인메모리 → Qdrant)이므로, 프롬프트 복잡도를 낮추고 인프라 변경의 효과를 격리하여 측정하기 위함.

> **학습 포인트**: 실험에서 한 번에 여러 변수를 바꾸면 "무엇이 개선에 기여했는지" 알 수 없다. Week 03은 의도적으로 프롬프트를 단순화하고 인프라만 변경.

### 소스 출처 추적 — 메타데이터 활용

```java
// 인제스트 시 메타데이터 추가
chunk.getMetadata().put("source", file.getOriginalFilename());

// 질의 시 출처 추출
List<String> sourceLabels = sources.stream()
    .map(doc -> (String) doc.getMetadata().getOrDefault("source", "unknown"))
    .distinct().toList();
```

"이 답변의 근거가 어떤 문서에서 왔는가?"를 응답에 포함. 프로덕션 RAG에서는 사용자 신뢰를 위해 필수적인 기능. Week 01-02에서는 FAQ 파일이 하나뿐이라 필요 없었지만, 문서가 여러 개가 되는 순간 출처가 중요해진다.

### Docker Compose — 원커맨드 환경 구성

```yaml
services:
  qdrant:
    image: qdrant/qdrant:latest
    ports:
      - "6333:6333"   # REST + Dashboard
      - "6334:6334"   # gRPC
    volumes:
      - qdrant-data:/qdrant/storage   # Named volume → 컨테이너 재시작해도 데이터 유지

  week03-app:
    environment:
      SPRING_AI_VECTORSTORE_QDRANT_HOST: qdrant  # Docker 서비스명으로 참조
    depends_on:
      - qdrant

volumes:
  qdrant-data:
```

`qdrant-data` Named Volume이 핵심: `docker compose down` → `docker compose up`해도 벡터 데이터가 유지된다. 이것이 InMemoryVectorStore와의 본질적인 차이.

---

## Spring AI API 키워드

| API | 설명 |
|-----|------|
| `VectorStore` | 벡터 저장/검색 추상화 인터페이스. `add()`, `similaritySearch()` |
| `SearchRequest` | 검색 요청. `.query()`, `.topK()`, `.similarityThreshold()` 등 설정 |
| `Document` | 텍스트 + 메타데이터 + 임베딩 벡터를 담는 핵심 데이터 클래스 |
| `TikaDocumentReader` | Apache Tika 기반 문서 리더. `Resource` → `List<Document>` |
| `TokenTextSplitter` | 토큰 수 기반 문서 분할기. `DocumentTransformer` 구현체 |
| `QdrantVectorStore` | Qdrant 벡터 DB의 `VectorStore` 구현체. auto-config 제공 |

---

## 테스트 결과

- `QueryControllerTest` — 통합 테스트 (Qdrant + OpenAI API 필요)
  - [x] POST /api/ingest → 파일 업로드 후 청크 수 반환
  - [x] POST /api/query → 관련 답변 + 소스 + 토큰 사용량 반환
  - [x] "반품 기간" 질문에 "30" 포함 확인
  - [x] 빈 질문 → 400
  - [x] 빈 파일 → 400

- `PersistenceTest` — 영속성 검증 (별도 Spring Context로 실행)
  - [x] 인제스트 없이 쿼리 성공 (이전 실행에서 저장된 데이터 사용)
  - [x] 재시작 후에도 "30일" 반품 정보 유지

- `PerformanceBenchmarkTest` — 성능 벤치마크
  - [x] 100/500/1000 청크에서 InMemory vs Qdrant 검색 시간 비교

---

## 배운 것 / 느낀 것

### InMemory → Qdrant 전환에서 체감한 것

Week 01에서 `cosineSimilarity()`를 직접 구현하고, `ArrayList`에 벡터를 저장하면서 "이건 프로덕션에서는 못 쓰겠다"를 느꼈다. Week 03에서 Qdrant로 전환하면서 그 한계가 구체적으로 해소되는 경험:

| Week 01-02 | Week 03 | 차이 |
|------------|---------|------|
| `ArrayList<EmbeddedChunk>` | Qdrant HNSW 인덱스 | O(n) → O(log n) |
| JVM 힙 메모리 | 디스크 + Named Volume | 재시작 시 소실 → 영속 |
| `Files.readString()` | `TikaDocumentReader` | 마크다운만 → 1000+ 포맷 |
| 직접 임베딩 + 저장 | `vectorStore.add()` | 수동 조립 → 한 줄 |

> **핵심 깨달음**: 추상화를 쓰기 전에 직접 구현해본 것이 결정적이었다. `vectorStore.similaritySearch()`가 내부적으로 하는 일(임베딩 → 벡터 비교 → 정렬 → top-K)을 알고 있으니, 검색 결과가 이상할 때 "임베딩 품질 문제인가, 인덱스 설정 문제인가, top-K가 부족한가?"를 구분할 수 있다.

### ETL 파이프라인 패턴의 보편성

```
Reader → Transformer → Writer
```

이 패턴은 RAG에만 국한되지 않는다:

- **데이터 엔지니어링**: CSV Reader → 정제 Transformer → DB Writer
- **검색 엔진**: Crawler → 인덱서 → 검색 인덱스
- **Spring Batch**: ItemReader → ItemProcessor → ItemWriter

Spring AI의 ETL이 `Document`를 중심 데이터 타입으로 사용하는 것은 이 보편적 패턴의 AI 특화 구현. `Document`에 텍스트, 메타데이터, 임베딩 벡터가 모두 담기므로, 파이프라인의 어느 단계에서든 필요한 정보에 접근할 수 있다.

### PersistenceTest의 설계 — "재시작 시뮬레이션"

```java
// PersistenceTest: 인제스트 없이 바로 쿼리
@Test
void queryWorksWithoutIngest() {
    mockMvc.perform(post("/api/query").content("{\"question\": \"반품 정책?\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.answer").isNotEmpty());
}
```

이 테스트의 전제: `QueryControllerTest`가 먼저 실행되어 Qdrant에 데이터가 인제스트된 상태. `PersistenceTest`는 **새로운 Spring Context**로 올라오므로 InMemoryVectorStore라면 데이터가 없어서 실패한다. Qdrant의 Named Volume 덕분에 통과.

> **테스트 설계 관점**: 이것은 단위 테스트가 아니라 **인프라 속성 테스트**다. "영속성"이라는 비기능적 요구사항을 테스트로 보장한 것. 테스트 실행 순서(`QueryControllerTest` → `PersistenceTest`)에 의존하므로, CI에서는 실행 순서를 명시하거나 setup fixture를 별도로 구성해야 한다.

### PerformanceBenchmarkTest — 숫자로 체감하기

벤치마크 구조:

```
100/500/1000 synthetic chunks
  x 2 backends (InMemory brute-force vs Qdrant ANN)
  x (3 warmup + 10 measured) queries
  → 평균 검색 시간 비교 테이블 출력
```

**왜 직접 벤치마크를 만들었는가**: "HNSW가 O(log n)이라 빠르다"는 이론적 설명만으로는 체감이 안 된다. 1000 청크에서 InMemory가 Xms, Qdrant가 Yms라는 구체적인 숫자를 보면:
- 현재 규모에서 실제 차이가 얼마인지 판단 가능
- "언제부터 Qdrant가 의미 있는 차이를 만드는가?"에 대한 답
- 네트워크 왕복(localhost이지만)이 포함된 Qdrant가 소규모에서는 오히려 느릴 수 있다는 반직관적 결과도 확인 가능

> **학습자 포인트**: 100 청크에서는 InMemory가 더 빠를 수 있다. 네트워크 오버헤드 없이 순수 계산만 하니까. 하지만 1000, 10000 청크에서는 HNSW의 O(log n)이 빛을 발한다. 벤치마크는 "어느 시점에서 전환이 유리한가?"를 판단하는 도구.

### `@SpringBootApplication`만으로 동작하는 이유 — auto-configuration

```java
@SpringBootApplication
public class Week03Application {
    public static void main(String[] args) {
        SpringApplication.run(Week03Application.class, args);
    }
}
```

Week 01에서는 `FaqLoader`, `InMemoryVectorStore` 등을 직접 Bean으로 등록했다. Week 03에서는 **커스텀 Bean이 하나도 없다**. Spring AI의 스타터 의존성이 자동으로 등록:

- `spring-ai-starter-model-openai` → `ChatClient.Builder`, `EmbeddingModel` 자동 Bean
- `spring-ai-starter-vector-store-qdrant` → `QdrantVectorStore` as `VectorStore` 자동 Bean
- `application.yml`의 설정만으로 연결 완료

> **Spring Boot의 가치를 재확인하는 순간**: Week 01에서 수동으로 조립하던 것이, 의존성 추가 + YAML 설정만으로 자동 구성된다. 하지만 Week 01을 거치지 않고 바로 auto-config를 쓰면 "왜 되는지" 모르고, 문제가 생기면 "왜 안 되는지"도 모른다.

### 파일 업로드 크기 제한 — 간과하기 쉬운 설정

```yaml
spring.servlet.multipart:
  max-file-size: 50MB
  max-request-size: 50MB
```

Spring Boot 기본값은 1MB. PDF 매뉴얼이 10-20MB인 경우가 많으므로 상향 필수. 이 설정이 없으면 큰 파일 인제스트 시 `MaxUploadSizeExceededException`이 발생하는데, 에러 메시지가 직관적이지 않아서 원인을 찾기 어렵다.

---

## 이 구현에서 느끼게 되는 한계 (Week 04+ 복선)

| 한계 | 언제 체감되는가 | 해결 주차 |
|------|----------------|-----------|
| top-K 중 노이즈 청크 | 비슷한 유사도의 청크가 5개인데 정답은 2개만 | Week 04 (ReRanker) |
| 단일 쿼리의 한계 | "비싼 무선 이어폰" → "가격" + "무선" + "이어폰" 복합 검색 필요 | Week 04 (쿼리 변환) |
| 임베딩 검색만으로는 키워드 매칭 부재 | 정확한 제품명 검색은 키워드가 더 정확 | Week 04 (하이브리드 검색) |
| 관측성 부재 | 검색 지연 원인을 모름 (임베딩? 네트워크? LLM?) | Week 05 (Micrometer, OTel) |
| 환각 탐지 불가 | LLM이 컨텍스트에 없는 답을 해도 감지 못함 | Week 05 (환각 탐지) |

---

## 다음 주차로 연결

Week 04에서 해결할 것:
- **ReRanker**: top-K 결과를 LLM으로 재순위화하여 정밀도 향상
- **쿼리 변환**: 사용자 질문을 검색에 최적화된 형태로 변환
- **하이브리드 검색**: 임베딩 유사도 + 키워드 매칭을 결합

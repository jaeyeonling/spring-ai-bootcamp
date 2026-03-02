---
name: spring-ai-api
description: Spring AI 핵심 API (ChatClient, EmbeddingModel, VectorStore, @Tool) 사용 가이드
---

# Spring AI API Guide

Spring AI 1.1.2 기준 핵심 API 사용법입니다.

## ChatClient

Spring AI의 중심 추상화. LLM과 대화하는 인터페이스입니다.

### 기본 사용

```java
@Autowired
ChatClient.Builder chatClientBuilder;

ChatClient chatClient = chatClientBuilder.build();

// 간단한 호출
String answer = chatClient.prompt()
    .user("안녕하세요")
    .call()
    .content();
```

### 시스템 프롬프트 + 사용자 메시지

```java
String answer = chatClient.prompt()
    .system("당신은 초록 코퍼레이션의 고객지원 담당자입니다.")
    .user("환불은 어떻게 받나요?")
    .call()
    .content();
```

### ChatResponse로 메타데이터 포함 응답

```java
ChatResponse response = chatClient.prompt()
    .system(systemPrompt)
    .user(question)
    .call()
    .chatResponse();

// 답변 텍스트
String answer = response.getResult().getOutput().getText();

// 토큰 사용량
Usage usage = response.getMetadata().getUsage();
long promptTokens = usage.getPromptTokens();
long completionTokens = usage.getCompletionTokens();
long totalTokens = usage.getTotalTokens();
```

### 옵션 설정

```java
chatClient.prompt()
    .options(ChatOptionsBuilder.builder()
        .model("gpt-4o-mini")
        .temperature(0.1)
        .build())
    .user(question)
    .call();
```

## EmbeddingModel

텍스트를 벡터로 변환하는 인터페이스입니다.

```java
@Autowired
EmbeddingModel embeddingModel;

// 단일 텍스트 임베딩
float[] vector = embeddingModel.embed("환불은 어떻게 받나요?");
// → [0.023, -0.041, 0.078, ...] (1536차원)

// Document 임베딩
EmbeddingResponse response = embeddingModel.embedForResponse(
    List.of("텍스트1", "텍스트2")
);
```

### 모델 설정

```yaml
# application.yml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      embedding:
        options:
          model: text-embedding-3-small
```

## VectorStore

벡터 검색을 추상화한 인터페이스입니다.

### 인터페이스

```java
public interface VectorStore {
    void add(List<Document> documents);
    Optional<Boolean> delete(List<String> idList);
    List<Document> similaritySearch(SearchRequest request);
}
```

### Qdrant 구현 (Week 03+)

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

// 문서 추가 (임베딩 자동 생성)
vectorStore.add(List.of(
    new Document("환불 정책은 14일 이내입니다.", Map.of("category", "returns"))
));

// 유사도 검색
List<Document> results = vectorStore.similaritySearch(
    SearchRequest.builder()
        .query("환불 방법 알려주세요")
        .topK(5)
        .similarityThreshold(0.7)
        .build()
);
```

## Document

Spring AI에서 텍스트와 메타데이터를 담는 기본 단위입니다.

```java
Document doc = new Document(
    "환불은 14일 이내에 가능합니다.",           // content
    Map.of("category", "returns", "source", "faq.md")  // metadata
);

String content = doc.getText();
Map<String, Object> metadata = doc.getMetadata();
```

## @Tool (Function Calling, Week 07)

LLM이 호출할 수 있는 함수를 정의합니다.

```java
@Component
public class OrderTools {

    @Tool(description = "주문 상태를 조회합니다. orderId는 'ORD-YYYY-NNN' 형식입니다.")
    public String getOrderStatus(
            @ToolParam(description = "조회할 주문 ID") String orderId) {
        // 구현
        return "주문 " + orderId + "은 배송 중입니다.";
    }
}
```

### ChatClient에 Tool 연결

```java
chatClient.prompt()
    .user("주문 #ORD-2024-001 상태를 알려주세요")
    .tools(new MethodToolCallbackProvider(orderTools))
    .call()
    .content();
```

## ETL 컴포넌트

### DocumentReader

```java
// Tika 기반 (Week 03+)
TikaDocumentReader reader = new TikaDocumentReader(resource);
List<Document> documents = reader.get();
```

### DocumentTransformer (TextSplitter)

```java
// 토큰 기반 분할 (Week 02+)
TokenTextSplitter splitter = new TokenTextSplitter();
List<Document> chunks = splitter.apply(documents);
```

### Evaluator (Week 02+)

```java
// 관련성 평가
RelevancyEvaluator evaluator = new RelevancyEvaluator(chatClient);
EvaluationResponse response = evaluator.evaluate(
    new EvaluationRequest(question, context, answer)
);
```

## MCP (Week 09)

Model Context Protocol로 도구를 표준화하여 외부 도구(Claude Desktop, Cursor)에서 사용 가능하게 합니다.

```java
// MCP 서버로 노출
@Tool(description = "FAQ를 검색합니다")
public String searchFaq(String query) {
    return ragService.search(query);
}
```

## 의존성 (build.gradle)

```groovy
// Week 01-02: OpenAI만
implementation 'org.springframework.ai:spring-ai-starter-model-openai'

// Week 03+: Qdrant 추가
implementation 'org.springframework.ai:spring-ai-starter-vector-store-qdrant'

// Week 03+: Tika 문서 리더
implementation 'org.springframework.ai:spring-ai-tika-document-reader'

// Week 05: 관측성
implementation 'org.springframework.boot:spring-boot-starter-actuator'
implementation 'io.micrometer:micrometer-registry-otlp'

// Week 09: MCP
implementation 'org.springframework.ai:spring-ai-starter-mcp-server'
```

## 관련 스킬

- `rag-pipeline`: RAG 전체 흐름
- `prompt-engineering`: 프롬프트 최적화
- `vector-store`: 벡터 저장소 상세

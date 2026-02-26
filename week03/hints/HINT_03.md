# 힌트 03: ChatClient를 이용한 RAG — VectorStore + 시스템 프롬프트 패턴

1주차-2주차에는 RAG를 수동으로 구현했습니다:

```java
// 1. 관련 청크 검색
List<Document> relevant = vectorStore.similaritySearch(
    SearchRequest.builder().query(question).topK(5).build()
);

// 2. 컨텍스트 문자열 구성
String context = relevant.stream()
    .map(Document::getText)
    .collect(Collectors.joining("\n\n"));

// 3. 컨텍스트를 주입한 프롬프트로 LLM 호출
String answer = chatClient.prompt()
    .system("이 컨텍스트에 기반하여 답하세요:\n\n" + context)
    .user(question)
    .call()
    .content();
```

이것이 Spring AI 1.1.2에서 올바른 접근 방식입니다. 이번 주에는 이 패턴을
개선하여 전체 `ChatResponse`(토큰 사용량)와 소스 문서도 반환합니다.

## 전체 응답 받기 (토큰 사용량 포함)

메타데이터에 접근하려면 `.content()` 대신 `.chatResponse()` 사용:

```java
import org.springframework.ai.chat.model.ChatResponse;

ChatResponse response = chatClient.prompt()
    .system("다음 컨텍스트만을 사용하여 질문에 답하세요:\n\n" + context)
    .user(question)
    .call()
    .chatResponse();

// 답변
String answer = response.getResult().getOutput().getText();

// 토큰 사용량
Usage usage = response.getMetadata().getUsage();
long promptTokens    = usage.getPromptTokens();
long completionTokens = usage.getCompletionTokens();
long totalTokens     = usage.getTotalTokens();
```

## 소스 문서 받기

컨텍스트 구성을 위해 이미 문서를 검색했습니다. 소스 레이블에도 재사용하세요:

```java
// 소스가 필요할 때는 명시적으로 검색
List<Document> sources = vectorStore.similaritySearch(
    SearchRequest.builder()
        .query(question)
        .topK(5)
        .build()
);

// 컨텍스트 구성
String context = sources.stream()
    .map(Document::getText)
    .collect(Collectors.joining("\n\n"));

// LLM 호출
ChatResponse response = chatClient.prompt()
    .system("다음 컨텍스트만을 사용하여 질문에 답하세요:\n\n" + context)
    .user(question)
    .call()
    .chatResponse();

// 문서 메타데이터에서 소스 레이블 추출
List<String> sourceLabels = sources.stream()
    .map(doc -> (String) doc.getMetadata().getOrDefault("source", "unknown"))
    .distinct()
    .toList();
```

## 완전한 쿼리 메서드 패턴

```java
@PostMapping("/query")
public ResponseEntity<QueryResponse> query(@RequestBody QueryRequest request) {
    if (request.question() == null || request.question().isBlank()) {
        return ResponseEntity.badRequest().build();
    }

    // 1. 관련 문서 검색
    List<Document> sources = vectorStore.similaritySearch(
        SearchRequest.builder().query(request.question()).topK(5).build()
    );

    // 2. 컨텍스트 문자열 구성
    String context = sources.stream()
        .map(Document::getText)
        .collect(Collectors.joining("\n\n"));

    // 3. 컨텍스트와 함께 LLM 호출
    ChatResponse response = chatClient.prompt()
        .system("다음 컨텍스트만을 사용하여 질문에 답하세요:\n\n" + context)
        .user(request.question())
        .call()
        .chatResponse();

    // 4. 결과 추출
    String answer = response.getResult().getOutput().getText();
    Usage usage = response.getMetadata().getUsage();
    List<String> sourceLabels = sources.stream()
        .map(doc -> (String) doc.getMetadata().getOrDefault("source", "unknown"))
        .distinct().toList();

    return ResponseEntity.ok(new QueryResponse(
        answer,
        sourceLabels,
        new TokenUsage(
            usage.getPromptTokens(),
            usage.getCompletionTokens(),
            usage.getTotalTokens()
        )
    ));
}
```

## Week 01 vs Week 03 비교

| 측면 | Week 01 (수동) | Week 03 (Qdrant) |
|--------|------------------|------------------|
| 검색 | 직접 코사인 루프 | `VectorStore.similaritySearch()` |
| 프롬프트 구성 | 문자열 연결 | 동일하지만 더 깔끔 |
| 영속성 | 없음 (인메모리) | Qdrant (디스크 기반) |
| 검색 속도 | O(n) 브루트 포스 | O(log n) ANN |
| 작성할 코드 | ~80줄 | ~30줄 |

수동 방식은 가치 있었습니다 — `VectorStore.similaritySearch()`가 내부적으로
무엇을 하는지 이해하게 됩니다.

## QuestionAnswerAdvisor 사용하기 (Spring AI 1.1.x+)

Spring AI 1.1.x부터 `QuestionAnswerAdvisor`가 공식 지원됩니다.
Advisor를 등록하면 VectorStore 검색 + 프롬프트 주입을 자동으로 처리해줍니다.

```java
import org.springframework.ai.rag.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;

// ChatClient 빌더에 Advisor 등록
ChatClient chatClient = chatClientBuilder
    .defaultAdvisors(
        new QuestionAnswerAdvisor(
            vectorStore,
            SearchRequest.builder().topK(5).build()
        )
    )
    .build();

// 사용 — Advisor가 자동으로 컨텍스트를 주입
String answer = chatClient.prompt()
    .user(userQuestion)
    .call()
    .content();
```

의존성에 `spring-ai-advisors-vector-store`가 포함되어 있어야 합니다
(BOM 1.1.x 이상 사용 시 `spring-ai-starter-model-openai`에 포함됨):

```groovy
// build.gradle — spring-ai-bom 1.1.x 사용 시 버전 생략 가능
implementation 'org.springframework.ai:spring-ai-advisors-vector-store'
```

### 직접 구현 방식도 여전히 유효

Advisor를 사용하지 않고 아래처럼 직접 구현해도 완전히 동일한 동작을 합니다.
내부 동작을 이해하거나 세밀한 제어가 필요할 때 유용합니다:

```java
// 수동 방식 — Advisor 없이 직접 구현 (1.0.x에서 유일한 방법이었음)
List<Document> docs = vectorStore.similaritySearch(
    SearchRequest.builder().query(question).topK(5).build()
);
String context = docs.stream()
    .map(Document::getText)
    .collect(Collectors.joining("\n\n"));

String answer = chatClient.prompt()
    .system("다음 컨텍스트를 기반으로 답변하세요:\n\n" + context)
    .user(question)
    .call()
    .content();
```

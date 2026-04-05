# 힌트 01: SSE 스트리밍 설정

## Spring AI 스트리밍 API

```java
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatClient chatClient;

    // 기존 동기 엔드포인트 (유지)
    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request) {
        String answer = chatClient.prompt().user(request.question()).call().content();
        return new ChatResponse(answer);
    }

    // 새로운 스트리밍 엔드포인트
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(@RequestParam String question) {
        return chatClient.prompt()
            .user(question)
            .stream()
            .content();  // Flux<String> — 토큰 단위 방출
    }
}
```

## curl로 테스트

```bash
curl -N "http://localhost:8080/api/chat/stream?question=반품%20기간이%20얼마나%20되나요"

# 출력 (토큰 단위로 순차 출력됨):
# 반품
#  기간은
#  구매
#  후
#  14일
#  이내입니다.
# ...
```

## RAG + 스트리밍 조합

```java
@GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> streamChat(@RequestParam String question) {
    // RAG 검색은 동기 (빠름, Flux 불필요)
    List<Document> docs = vectorStore.similaritySearch(
        SearchRequest.query(question).withTopK(5)
    );
    String systemPrompt = buildSystemPrompt(docs);

    // 응답은 스트리밍
    return chatClient.prompt()
        .system(systemPrompt)
        .user(question)
        .stream()
        .content();
}
```

## SSE 이벤트 구조화

토큰 단위 텍스트 외에 구조화된 이벤트를 보낼 수 있습니다:

```java
@GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<String>> streamWithEvents(@RequestParam String question) {
    Flux<String> tokens = chatClient.prompt()
        .user(question)
        .stream()
        .content();

    // 시작 이벤트
    Flux<ServerSentEvent<String>> start = Flux.just(
        ServerSentEvent.<String>builder()
            .event("start")
            .data("{\"status\": \"streaming\"}")
            .build()
    );

    // 토큰 이벤트
    Flux<ServerSentEvent<String>> content = tokens.map(token ->
        ServerSentEvent.<String>builder()
            .event("token")
            .data(token)
            .build()
    );

    // 완료 이벤트
    Flux<ServerSentEvent<String>> done = Flux.just(
        ServerSentEvent.<String>builder()
            .event("done")
            .data("{\"status\": \"complete\"}")
            .build()
    );

    return Flux.concat(start, content, done);
}
```

## WebMVC vs WebFlux

Spring AI 스트리밍은 두 환경 모두 지원합니다:

```gradle
// WebFlux (권장 — 스트리밍에 최적)
implementation 'org.springframework.boot:spring-boot-starter-webflux'

// WebMVC (기존 프로젝트)
implementation 'org.springframework.boot:spring-boot-starter-web'
// WebMVC에서도 SseEmitter로 스트리밍 가능 (HINT_02 참고)
```

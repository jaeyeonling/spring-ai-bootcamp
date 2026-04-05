# 힌트 03: 스트리밍 에러 처리와 Tool Use 조합

## 스트리밍 중 에러 처리

```java
return chatClient.prompt()
    .user(question)
    .stream()
    .content()
    .onErrorResume(e -> {
        log.error("스트리밍 오류: {}", e.getMessage());
        // 오류 메시지를 스트림으로 전송 후 종료
        return Flux.just("죄송합니다, 일시적인 오류가 발생했습니다. 다시 시도해 주세요.");
    })
    .timeout(Duration.ofSeconds(30), Flux.just("[타임아웃] 응답 시간이 초과됐습니다."));
```

## Tool Use + 스트리밍

Tool 호출 중에는 스트리밍이 잠시 멈춥니다. UX를 위해 중간 상태를 알려주세요.

```java
@GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<String>> streamWithTools(@RequestParam String question) {

    // 1. Tool 필요 여부 먼저 판단 (빠름)
    boolean needsTool = requiresTool(question);

    if (needsTool) {
        // 2. "조회 중..." 이벤트 먼저 전송
        Flux<ServerSentEvent<String>> waitMessage = Flux.just(
            ServerSentEvent.<String>builder()
                .event("tool-call")
                .data("{\"message\": \"실시간 정보를 조회하고 있습니다...\"}")
                .build()
        );

        // 3. Tool 호출 후 결과 스트리밍
        Flux<ServerSentEvent<String>> response = chatClient.prompt()
            .user(question)
            .stream()
            .content()
            .map(token -> ServerSentEvent.<String>builder().data(token).build());

        return Flux.concat(waitMessage, response);
    }

    // Tool 불필요: 바로 스트리밍
    return chatClient.prompt()
        .user(question)
        .stream()
        .content()
        .map(token -> ServerSentEvent.<String>builder().data(token).build());
}
```

## 스트리밍 + OTel 트레이싱

스트리밍 환경에서 OTel trace가 올바르게 닫히는지 확인하세요.

```yaml
# application.yml
management:
  tracing:
    enabled: true
spring:
  ai:
    chat:
      observations:
        enabled: true  # ChatClient 자동 계측
```

Zipkin에서 스트리밍 요청의 trace 확인:
- RAG 검색 span
- LLM 호출 span (스트리밍 중 열려있다가 완료 시 닫힘)
- 전체 요청 span

## 스트리밍 응답 시간 비교

```
동기 응답:   7초 후 전체 텍스트 (TTFB: 7초)
스트리밍:    0.5초 후 첫 토큰, 이후 7초에 걸쳐 전체 (TTFB: 0.5초)
```

TTFB(Time To First Byte)가 UX에 미치는 영향을 체감해보세요.
같은 LLM 처리 시간이지만 사용자 경험이 크게 다릅니다.

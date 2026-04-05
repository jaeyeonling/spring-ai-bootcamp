# 힌트 02: 클라이언트 인터럽트와 토큰 절약

## 인터럽트의 의미

클라이언트가 연결을 끊으면:
1. Flux가 취소됨 (Project Reactor)
2. OpenAI API 호출이 중단됨
3. 남은 토큰에 대한 비용 지불 없음

## 기본 인터럽트 처리

WebFlux에서는 클라이언트 연결 끊김이 자동으로 Flux를 취소합니다:

```java
@GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> streamChat(@RequestParam String question) {
    return chatClient.prompt()
        .user(question)
        .stream()
        .content()
        .doOnSubscribe(s -> log.info("스트리밍 시작: {}", question))
        .doOnCancel(() -> log.info("클라이언트 연결 끊김 — 스트리밍 중단"))
        .doOnComplete(() -> log.info("스트리밍 완료"))
        .doOnError(e -> log.warn("스트리밍 오류: {}", e.getMessage()));
}
```

## 토큰 절약 측정

인터럽트 전후 토큰 사용량 차이를 측정하세요:

```java
@GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<String>> streamWithMetrics(@RequestParam String question) {
    AtomicInteger tokenCount = new AtomicInteger(0);
    long startTime = System.currentTimeMillis();

    return chatClient.prompt()
        .user(question)
        .stream()
        .content()
        .map(token -> {
            tokenCount.incrementAndGet();
            return ServerSentEvent.<String>builder().data(token).build();
        })
        .doOnCancel(() -> {
            int generated = tokenCount.get();
            log.info("인터럽트: {}토큰 생성 후 중단 ({}ms)", generated,
                System.currentTimeMillis() - startTime);
            metrics.recordInterrupt(generated);
        })
        .doOnComplete(() -> {
            log.info("완료: {}토큰 ({}ms)", tokenCount.get(),
                System.currentTimeMillis() - startTime);
        });
}
```

## WebMVC에서 인터럽트 (SseEmitter)

WebFlux를 사용하지 않는 경우:

```java
@GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter streamChat(@RequestParam String question) {
    SseEmitter emitter = new SseEmitter(30_000L);  // 30초 타임아웃

    // 비동기 실행
    executor.execute(() -> {
        try {
            chatClient.prompt()
                .user(question)
                .stream()
                .content()
                .doOnCancel(() -> emitter.complete())
                .subscribe(
                    token -> {
                        try {
                            emitter.send(SseEmitter.event().data(token));
                        } catch (IOException e) {
                            emitter.completeWithError(e);
                        }
                    },
                    emitter::completeWithError,
                    emitter::complete
                );
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    });

    emitter.onTimeout(emitter::complete);
    emitter.onError(e -> log.warn("SSE 오류: {}", e.getMessage()));
    return emitter;
}
```

## 인터럽트 테스트

```bash
# 연결 후 0.5초 뒤 강제 종료
curl -N --max-time 0.5 "http://localhost:8080/api/chat/stream?question=..." 2>/dev/null

# 로그에서 확인:
# "클라이언트 연결 끊김 — 스트리밍 중단" 출력 여부
# 몇 토큰 생성 후 중단됐는지
```

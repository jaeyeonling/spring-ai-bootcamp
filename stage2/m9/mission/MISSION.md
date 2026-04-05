# M9 미션: 스트리밍 & 응답 제어

## Stage 1에서 겪은 벽

```
고객이 질문을 보냈습니다.

[3초 경과] ...

[5초 경과] ...

[7초 경과] 답변이 도착했습니다.
```

LLM이 전체 답변을 완성하기 전까지 클라이언트는 빈 화면을 봅니다.
답변 방향이 잘못됐어도 멈출 수 없습니다.
잘못된 방향으로 가는 토큰에 비용을 지불합니다.

---

## 미션

SSE 스트리밍 응답을 구현하고, 클라이언트가 중간에 취소하면 LLM 호출을 중단하세요.

이 모듈은 **M1 없이도 바로 적용 가능**합니다.
Stage 1 직후에 선택해도 됩니다.

### 완료 기준

```
[ ] GET /api/chat/stream 엔드포인트: SSE로 토큰 단위 스트리밍
[ ] 클라이언트 연결 끊김 감지 및 LLM 호출 중단
[ ] 중단 시 불필요한 토큰 생성 비용 차단 확인
[ ] 스트리밍 중 오류 발생 시 에러 이벤트 전송
```

---

## Spring AI 스트리밍 API

```java
@GetMapping(value = "/api/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> streamChat(@RequestParam String question) {
    return chatClient.prompt()
        .user(question)
        .stream()
        .content();  // Flux<String> — 토큰 단위로 방출
}
```

SSE 클라이언트 (curl로 테스트):
```bash
curl -N "http://localhost:8080/api/chat/stream?question=반품%20기간이%20얼마나%20되나요"
```

---

## 클라이언트 인터럽트

클라이언트가 연결을 끊으면 스트리밍도 중단되어야 합니다.
Project Reactor의 `doOnCancel()`로 취소를 감지합니다:

```java
return chatClient.prompt()
    .user(question)
    .stream()
    .content()
    .doOnCancel(() -> log.info("클라이언트 연결 끊김 — 스트리밍 중단"))
    .doOnComplete(() -> log.info("스트리밍 완료"));
```

WebFlux가 클라이언트 연결 끊김을 감지하면 자동으로 Flux를 취소합니다.
LLM API 호출도 함께 중단됩니다.

---

## 스트리밍 + RAG

RAG 컨텍스트를 검색한 후 응답을 스트리밍하려면:

```java
@GetMapping(value = "/api/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> streamChat(@RequestParam String question) {
    // 1. RAG 검색 (동기, 빠름)
    List<Document> context = vectorStore.similaritySearch(question);

    // 2. 컨텍스트를 포함하여 스트리밍
    String systemPrompt = buildSystemPrompt(context);

    return chatClient.prompt()
        .system(systemPrompt)
        .user(question)
        .stream()
        .content();
}
```

---

## Tool Use + 스트리밍 조합 (M4 이후)

Tool 호출은 동기입니다. 스트리밍 중에 Tool을 호출하면 UX가 어색해집니다.

```
[토큰 스트리밍 중...]
[Tool 호출 대기 중... 2초]
[다시 토큰 스트리밍...]
```

이 문제를 어떻게 처리할지 설계 포인트입니다:
- Tool 호출 전 "잠시만요..." 메시지 스트리밍
- Tool 호출을 별도 엔드포인트로 분리
- SSE 이벤트 타입으로 Tool 호출 상태 전달

---

## 힌트

| 힌트 | 이럴 때 열어보세요 |
|------|-------------------|
| `HINT_01.md` | Flux<String> SSE 설정 및 Content-Type이 막힐 때 |
| `HINT_02.md` | 클라이언트 연결 끊김 감지가 동작하지 않을 때 |
| `HINT_03.md` | 스트리밍 중 에러 처리 및 Tool Use 조합이 막힐 때 |

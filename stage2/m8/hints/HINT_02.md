# 힌트 02: 토큰 예산 관리

대화가 길어지면 이전 메시지들이 토큰을 잡아먹습니다.

## 문제 파악

```
1턴: 시스템(500) + RAG 컨텍스트(1000) + 질문(50) + 답변(200) = 1,750 토큰
5턴: 시스템(500) + 대화기록(5×250=1,250) + RAG(1,000) + 질문(50) = 2,800 토큰
20턴: 시스템(500) + 대화기록(20×250=5,000) + RAG(1,000) + 질문(50) = 6,550 토큰
```

gpt-4.1-nano 128K 컨텍스트에서는 여유롭지만 **비용은 선형으로 증가**합니다.

## 슬라이딩 윈도우

`CHAT_MEMORY_RETRIEVE_SIZE_KEY`로 유지할 턴 수를 제한합니다:

```java
// 최근 10턴만 유지 (Spring AI 기본 지원)
.param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10)
```

단점: 오래된 중요 정보가 사라집니다.
"처음에 말씀하신 주문번호가..."가 기억이 안 날 수 있습니다.

## 요약 기반 압축

대화가 길어지면 오래된 부분을 요약으로 대체합니다:

```java
@Service
public class ConversationSummarizer {

    public String summarize(List<Message> messages) {
        if (messages.size() <= 6) return null;  // 짧으면 요약 불필요

        // 오래된 N개 메시지를 요약
        List<Message> toSummarize = messages.subList(0, messages.size() - 6);

        return chatClient.prompt()
            .user("""
                다음 대화를 핵심 정보만 남겨 2-3문장으로 요약하세요.
                주문번호, 상품명, 특별한 상황 등 중요 컨텍스트는 반드시 포함하세요.

                대화:
                %s
                """.formatted(formatMessages(toSummarize)))
            .call()
            .content();
    }
}
```

```java
// 요약을 시스템 메시지로 주입
public String askWithSummary(String sessionId, String question) {
    List<Message> history = chatMemory.get(sessionId, 100);

    String summary = summarizer.summarize(history);

    return chatClient.prompt()
        .system(summary != null
            ? "이전 대화 요약: " + summary
            : "")
        .user(question)
        .advisors(a -> a
            .param(CHAT_MEMORY_CONVERSATION_ID_KEY, sessionId)
            .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 6))  // 최근 6턴 + 요약
        .call()
        .content();
}
```

## 토큰 사용량 추적

대화가 길어질수록 토큰이 얼마나 증가하는지 측정하세요:

```java
ChatResponse response = chatClient.prompt()
    .user(question)
    .advisors(a -> a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, sessionId))
    .call()
    .chatResponse();

Usage usage = response.getMetadata().getUsage();
log.info("세션 {} 턴 {}: 프롬프트 {}토큰",
    sessionId, getTurnCount(sessionId), usage.getPromptTokens());
```

10턴 대화에서 1턴 대비 토큰이 몇 배 증가하는지 확인하세요.

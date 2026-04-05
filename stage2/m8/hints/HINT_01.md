# 힌트 01: MessageChatMemoryAdvisor와 세션 관리

## Spring AI ChatMemory 기본 설정

```java
@Configuration
public class ChatConfig {

    @Bean
    public ChatMemory chatMemory() {
        return new InMemoryChatMemory();  // 개발용, 재시작 시 초기화
        // 프로덕션: CassandraChatMemory 또는 Redis 기반 구현
    }

    @Bean
    public ChatClient chatClient(ChatModel chatModel,
                                  ChatMemory chatMemory,
                                  VectorStore vectorStore) {
        return ChatClient.builder(chatModel)
            .defaultAdvisors(
                new MessageChatMemoryAdvisor(chatMemory),  // 대화 기억
                new QuestionAnswerAdvisor(vectorStore)      // RAG
            )
            .build();
    }
}
```

## 세션 ID로 대화 분리

```java
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatClient chatClient;

    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request) {
        String sessionId = request.sessionId();  // 클라이언트가 전달

        String answer = chatClient.prompt()
            .user(request.question())
            .advisors(advisor -> advisor
                .param(CHAT_MEMORY_CONVERSATION_ID_KEY, sessionId)
                .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10)  // 최근 10턴 유지
            )
            .call()
            .content();

        return new ChatResponse(answer);
    }
}
```

API 명세 변경:
```json
// 요청
{
  "sessionId": "user-abc123",   // 클라이언트가 생성하고 유지
  "question": "그러면 어떻게 신청해요?"
}
```

## 멀티턴 대화 테스트

```java
@Test
void 멀티턴_대화_테스트() {
    String sessionId = UUID.randomUUID().toString();

    // 첫 번째 턴
    String response1 = chatClient.prompt()
        .user("반품하고 싶어요")
        .advisors(a -> a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, sessionId))
        .call().content();

    // 두 번째 턴 — "어떻게"가 무엇을 가리키는지 대화 기록으로 파악해야 함
    String response2 = chatClient.prompt()
        .user("어떻게 신청해요?")
        .advisors(a -> a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, sessionId))
        .call().content();

    // 두 번째 응답에 "반품" 관련 내용이 있어야 함
    assertThat(response2).containsIgnoringCase("반품");
}
```

## 세션 만료 처리

```java
@Scheduled(fixedRate = 1800000)  // 30분마다
public void cleanExpiredSessions() {
    // InMemoryChatMemory는 세션 만료 기능이 없음
    // 직접 구현 필요
    sessionLastActivity.entrySet().stream()
        .filter(e -> Duration.between(e.getValue(), Instant.now()).toMinutes() > 30)
        .map(Map.Entry::getKey)
        .forEach(sessionId -> {
            chatMemory.clear(sessionId);
            sessionLastActivity.remove(sessionId);
            log.debug("세션 만료: {}", sessionId);
        });
}
```

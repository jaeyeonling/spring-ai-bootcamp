# 힌트 02: 파싱 실패 재시도와 폴백

## 파싱 실패 원인

LLM이 JSON 형식을 지키지 않을 때:

```
기대: {"answer": "...", "category": "RETURNS", "confidence": 0.9, ...}
실제: "네, 반품 기간은 14일입니다. 카테고리: 반품"  ← 자유 텍스트
실제: {"answer": "...", "category": "returns", ...}  ← 소문자 (enum 매핑 실패)
실제: {"answer": "..."}  ← 필드 누락
```

## 자동 재시도

```java
@Service
public class StructuredChatService {

    private static final int MAX_RETRIES = 3;

    public ChatAnswer ask(String question, List<Document> context) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return chatClient.prompt()
                    .system(buildSystemPrompt(context))
                    .user(attempt == 1 ? question : question + buildRetryHint(attempt))
                    .call()
                    .entity(ChatAnswer.class);

            } catch (Exception e) {
                lastException = e;
                log.warn("구조화 파싱 실패 (시도 {}/{}): {}", attempt, MAX_RETRIES, e.getMessage());

                if (attempt < MAX_RETRIES) {
                    Thread.sleep(100L * attempt);  // 점진적 대기
                }
            }
        }

        // 3회 모두 실패 시 폴백
        log.error("구조화 파싱 최종 실패, 폴백 사용", lastException);
        return fallback(question, context);
    }

    private String buildRetryHint(int attempt) {
        return "\n\n[중요] 반드시 요청한 JSON 형식으로만 응답하세요. 설명 텍스트를 포함하지 마세요.";
    }

    private ChatAnswer fallback(String question, List<Document> context) {
        // 구조화 실패 시 자유 텍스트로 답변 받고, 최소 필드만 채움
        String answer = chatClient.prompt()
            .system(buildSystemPrompt(context))
            .user(question)
            .call()
            .content();

        return new ChatAnswer(
            answer,
            Category.UNKNOWN,
            0.5,
            false
        );
    }
}
```

## 파싱 실패율 추적

```java
registry.counter("structured.output.success").increment();  // 성공
registry.counter("structured.output.retry", "attempt", String.valueOf(attempt)).increment();
registry.counter("structured.output.fallback").increment();  // 폴백 사용
```

## 유연한 스키마 설계

너무 엄격한 스키마는 파싱 실패율을 높입니다:

```java
// 너무 엄격 — LLM이 자주 실패
public record ChatAnswer(
    String answer,
    Category category,        // enum 값만 허용
    double confidence,        // 정확히 0.0-1.0
    List<String> sourceFiles, // 항상 채워야 함
    boolean requiresHumanAgent
) {}

// 적절히 유연 — 실패율 낮음
public record ChatAnswer(
    String answer,            // 필수
    @Nullable String category, // null 허용
    @Nullable Double confidence // null 허용
) {}
```

핵심 필드만 강제하고, 부가 정보는 nullable로 설계하세요.

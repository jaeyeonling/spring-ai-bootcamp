# 힌트 03: Spring AI로 Reflection 패턴 구현

## Reflection이란?

Reflection 패턴은 LLM이 사용자에게 반환하기 전에 **자신의 출력을 검증**하도록 합니다.
신뢰성을 향상시키는 단순하지만 강력한 방법입니다:

```
사용자 질문
    -> 생성 (툴 호출, 결과 얻기)
    -> 검증 ("이것이 질문에 올바르게 답하나요?")
    -> 틀렸다면: 재시도 (다른 방식 시도)
    -> 맞다면: 사용자에게 반환
```

사람이 자신의 작업을 꼼꼼히 재확인하는 방식과 유사합니다.

## 툴 호출에서 Reflection이 중요한 이유

툴 호출은 미묘한 방식으로 잘못될 수 있습니다:
- LLM이 올바른 툴을 호출하지만 잘못된 파라미터로 ("주문 #123" 대신 "ORD-2024-123")
- 툴이 데이터를 반환하지만 LLM이 잘못 해석
- 툴 호출이 실패하고 LLM이 오류 메시지를 실제 답변처럼 제시

Reflection 없이는 이런 오류가 바로 사용자에게 전달됩니다. Reflection이 있으면
LLM이 이를 발견하고 수정합니다.

## 기본 구현

```java
@Service
public class ReflectionService {

    private static final int MAX_RETRIES = 2;

    private final ChatClient chatClient;

    public ReflectionService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public String chatWithReflection(String userMessage, Object... tools) {
        String result = null;

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            // 1단계: 생성 — 툴과 함께 LLM 호출
            result = chatClient.prompt()
                .system("당신은 도움이 되는 어시스턴트입니다. 제공된 툴을 사용해 질문에 답하세요.")
                .user(userMessage)
                .tools(tools)
                .call()
                .content();

            // 2단계: 검증 — LLM에게 결과가 올바른지 물어봄
            String verification = chatClient.prompt()
                .system("""
                    당신은 품질 검사관입니다. 다음 답변이 사용자의 질문을
                    올바르고 완전하게 해결하는지 평가하세요.
                    "PASS" 또는 "FAIL: <이유>"만 응답하세요.
                    """)
                .user("""
                    질문: %s
                    답변: %s
                    """.formatted(userMessage, result))
                .call()
                .content();

            // 3단계: 검증 결과 확인
            if (verification.trim().startsWith("PASS")) {
                return result;  // 검증됨 — 사용자에게 반환
            }

            // 4단계: FAIL이면 로그하고 재시도
            log.warn("시도 {}에서 Reflection 실패, 질문 '{}': {}",
                attempt + 1, userMessage, verification);
        }

        // 재시도 소진 — 경고와 함께 마지막 결과 반환
        return result + "\n\n(참고: 이 답변을 완전히 검증할 수 없었습니다.)";
    }
}
```

## Retry + Circuit Breaker와의 비교

| 패턴 | 하는 일 | 도움이 되는 경우 |
|---------|-------------|---------------|
| **Retry** (Resilience4j) | 실패 시 같은 작업 재실행 | 네트워크 오류, 타임아웃 |
| **Circuit Breaker** | 실패하는 서비스 호출 중단 | 서비스 장애 |
| **Reflection** | 접근 방식을 다시 추론 | 잘못된 툴, 잘못된 파라미터, 잘못된 해석 |

Retry는 **일시적 실패** (툴 호출 자체가 실패)를 처리합니다.
Reflection은 **의미적 실패** (툴 호출은 성공했지만 답변이 틀림)를 처리합니다.

서로 보완 관계입니다:

```java
// Retry는 네트워크 문제 처리
@Retry(name = "toolCall", maxAttempts = 3)
public String callTool(String input) {
    return externalApi.call(input);
}

// Reflection은 잘못된 답변 처리
public String chatWithReflection(String question) {
    // 툴 호출이 성공하더라도 답변이 틀릴 수 있습니다
    // Reflection이 그것을 잡아냅니다
}
```

## 고급: 구조화된 검증

자유 텍스트 검증 대신 구조화된 응답 사용:

```java
public record VerificationResult(
    boolean isCorrect,
    String reason,
    String suggestedAction  // "retry_with_different_params", "use_different_tool", "accept"
) {}

// Spring AI의 entity()로 구조화된 출력 얻기
VerificationResult verification = chatClient.prompt()
    .system("답변을 평가하세요. isCorrect, reason, suggestedAction이 포함된 JSON을 반환하세요.")
    .user("질문: %s\n답변: %s".formatted(question, answer))
    .call()
    .entity(VerificationResult.class);

if (!verification.isCorrect()) {
    // suggestedAction을 사용해 다음에 무엇을 할지 결정
}
```

## Reflection을 사용하지 말아야 할 경우

Reflection은 지연시간(추가 LLM 호출)과 비용을 추가합니다. 다음의 경우에는 건너뛰세요:
- 툴 호출이 결정론적이고 항상 올바른 경우 (예: 단순 조회)
- 정확도보다 지연시간이 더 중요한 경우
- 추가 LLM 호출 비용이 가끔 발생하는 잘못된 답변 비용보다 큰 경우

다음의 경우에 사용하세요:
- 툴 호출에 복잡한 파라미터 추출이 포함된 경우
- 잘못된 답변이 높은 비즈니스 영향을 미치는 경우
- 실수를 더 많이 하는 소규모/덜 유능한 모델을 사용하는 경우

# 힌트 01: BeanOutputConverter와 entity() 사용법

## 기본 사용법

```java
// 응답 스키마 정의
public record ChatAnswer(
    String answer,
    String category,
    double confidence,
    boolean requiresHumanAgent
) {}
```

```java
// 구조화 응답 받기
ChatAnswer result = chatClient.prompt()
    .system("""
        당신은 초록 코퍼레이션 고객지원 챗봇입니다.
        주어진 컨텍스트를 기반으로 질문에 답하세요.

        컨텍스트:
        {context}
        """)
    .user(question)
    .call()
    .entity(ChatAnswer.class);

log.info("카테고리: {}, 신뢰도: {:.0%}", result.category(), result.confidence());
if (result.requiresHumanAgent()) {
    notifyAgent(question);
}
```

## 내부 동작

`entity(ChatAnswer.class)` 호출 시 Spring AI가 자동으로 프롬프트 끝에 추가합니다:

```
응답은 다음 JSON 형식만 출력하세요:
{
  "answer": "string",
  "category": "string",
  "confidence": "number (0.0-1.0)",
  "requiresHumanAgent": "boolean"
}
```

## enum으로 category 제약

자유 텍스트 대신 enum을 사용하면 유효한 카테고리만 반환됩니다:

```java
public enum Category {
    ORDERS, SHIPPING, RETURNS, PAYMENT, ACCOUNT,
    SUPPORT, PACKAGING, SUBSCRIPTION, FEEDBACK,
    INVOICE, ECO_GREEN, MARKETPLACE, UNKNOWN
}

public record ChatAnswer(
    String answer,
    Category category,      // enum 사용
    double confidence,
    boolean requiresHumanAgent
) {}
```

## List 반환

여러 항목을 구조화된 배열로 받을 수 있습니다:

```java
// 여러 추천 항목
List<FaqRecommendation> recommendations = chatClient.prompt()
    .user("배송 관련 자주 묻는 질문 3개 추천해줘")
    .call()
    .entity(new ParameterizedTypeReference<List<FaqRecommendation>>() {});

public record FaqRecommendation(String question, String category) {}
```

## 디버깅: 실제 프롬프트 확인

LLM에게 어떤 프롬프트가 전달되는지 확인하려면:

```yaml
logging:
  level:
    org.springframework.ai.chat.client: DEBUG
```

로그에서 `[SYSTEM]`, `[USER]` 섹션을 찾아보세요.
Spring AI가 추가한 JSON 스키마 지시사항을 확인할 수 있습니다.

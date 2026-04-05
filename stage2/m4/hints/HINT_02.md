# 힌트 02: Tool Description 설계 — 라우팅의 핵심

LLM은 description만 보고 어떤 Tool을 호출할지 결정합니다.
description이 모호하면 라우팅이 실패합니다.

## 나쁜 Description vs 좋은 Description

**나쁜 예:**

```java
@Tool(description = "주문 조회")
public OrderStatus getOrderStatus(String orderId) { ... }

@Tool(description = "배송 조회")
public TrackingInfo trackDelivery(String orderId) { ... }
```

"주문 조회"와 "배송 조회"는 LLM 입장에서 모호합니다.
"주문 어디 있어요?"가 어느 Tool인지 판단하기 어렵습니다.

**좋은 예:**

```java
@Tool(description = "주문의 처리 상태를 조회합니다. "
    + "결제 완료 여부, 상품 준비 중인지, 이미 발송됐는지 확인할 때 사용합니다. "
    + "배송 위치 추적은 이 Tool이 아닌 trackDelivery를 사용하세요.")
public OrderStatus getOrderStatus(String orderId) { ... }

@Tool(description = "발송된 상품의 현재 배송 위치와 예상 도착일을 조회합니다. "
    + "'지금 어디 있어요', '언제 와요', '배송 추적' 같은 질문에 사용합니다. "
    + "주문이 아직 발송 전이라면 getOrderStatus를 먼저 확인하세요.")
public TrackingInfo trackDelivery(String orderId) { ... }
```

## Description 설계 원칙

1. **언제 쓰는가** — 사용 시점을 명시
2. **언제 안 쓰는가** — 유사 Tool과의 구분
3. **입력 형식** — 파라미터가 뭘 기대하는지
4. **출력 내용** — 무엇을 반환하는지

## Tool 라우팅 테스트

description을 바꿀 때마다 라우팅이 올바른지 테스트하세요:

```java
@ParameterizedTest
@CsvSource({
    "제 주문 20240815-042 상태가 어떻게 되나요?, getOrderStatus",
    "배송 지금 어디쯤 왔어요?, trackDelivery",
    "오늘 할인 행사 있나요?, getActivePromotions",
    "반품 정책이 어떻게 되나요?, null"  // RAG, Tool 없음
})
void tool_라우팅_테스트(String question, String expectedTool) {
    // ChatClient.call() 후 어떤 Tool이 호출됐는지 확인
    // Spring AI의 toolCallMetadata로 검증
}
```

## Reflection 패턴 (자기 검증)

Tool 결과가 신뢰할 수 없을 때, LLM이 스스로 검증하고 재시도합니다:

```java
public String askWithReflection(String question) {
    String answer = chatClient.prompt().user(question).call().content();

    // 검증
    String verification = chatClient.prompt()
        .user("""
            질문: %s
            답변: %s

            이 답변이 정확하고 완전한가? 개선이 필요하다면 수정하세요.
            JSON: {"is_correct": true/false, "improved_answer": "...or null"}
            """.formatted(question, answer))
        .call()
        .content();

    VerificationResult result = parse(verification);
    return result.isCorrect() ? answer : result.improvedAnswer();
}
```

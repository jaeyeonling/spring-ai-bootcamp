# 힌트 01: @Tool 등록과 ChatClient 연결

## @Tool 기본 사용법

```java
@Service
public class OrderTools {

    private final OrderRepository orderRepository;

    @Tool(description = "특정 주문의 현재 상태를 조회합니다. "
        + "주문번호 형식은 YYYYMMDD-NNN입니다. "
        + "배송 위치가 아닌 주문 처리 상태(결제완료/준비중/발송됨/완료)를 반환합니다.")
    public OrderStatus getOrderStatus(
            @ToolParam(description = "조회할 주문번호 (예: 20240815-042)") String orderId) {
        return orderRepository.findById(orderId)
            .map(Order::status)
            .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다: " + orderId));
    }

    @Tool(description = "배송 중인 상품의 현재 위치와 예상 도착일을 조회합니다. "
        + "발송된 주문에만 사용 가능합니다.")
    public TrackingInfo trackDelivery(
            @ToolParam(description = "추적할 주문번호") String orderId) {
        return deliveryService.getTrackingInfo(orderId);
    }

    @Tool(description = "현재 진행 중인 프로모션과 할인 정보를 조회합니다. "
        + "특정 상품 카테고리 필터링 가능합니다.")
    public List<Promotion> getActivePromotions(
            @ToolParam(description = "상품 카테고리 (선택사항, null이면 전체)") String category) {
        return promotionService.getActive(category);
    }
}
```

## ChatClient에 Tool 등록

```java
@Configuration
public class ChatConfig {

    @Bean
    public ChatClient chatClient(ChatModel chatModel, OrderTools orderTools) {
        return ChatClient.builder(chatModel)
            .defaultTools(orderTools)          // Tool 등록
            .defaultAdvisors(
                new QuestionAnswerAdvisor(vectorStore)  // RAG도 함께
            )
            .build();
    }
}
```

## LLM이 자동으로 선택

```java
// 사용 측에서는 그냥 질문하면 됨 — LLM이 Tool 또는 RAG 중 선택
String answer = chatClient.prompt()
    .user("제 주문 20240815-042 지금 어디 있어요?")
    .call()
    .content();

// LLM이 판단:
// → "주문번호가 있다" → OrderTools.trackDelivery() 호출
// → "반품 정책 질문" → RAG 검색
```

## Tool 실행 로그 확인

```yaml
logging:
  level:
    org.springframework.ai: DEBUG  # Tool 호출 로그 확인
```

로그에서 확인할 것:
```
Tool 선택: trackDelivery(orderId=20240815-042)
Tool 결과: TrackingInfo{location="인천 물류센터", eta="2024-08-16"}
```

## Mock Tool (테스트용)

실제 DB 없이 테스트하려면:

```java
@Tool(description = "...")
public OrderStatus getOrderStatus(String orderId) {
    // Mock 데이터
    return switch (orderId) {
        case "20240815-042" -> new OrderStatus("배송중", "인천 물류센터");
        case "20240815-001" -> new OrderStatus("완료", null);
        default -> throw new IllegalArgumentException("주문 없음: " + orderId);
    };
}
```

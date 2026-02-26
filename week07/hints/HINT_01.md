# 힌트 01: Spring AI @Tool 어노테이션

## 함수 호출 동작 방식

함수 호출(툴 사용이라고도 함)은 LLM이 여러분의 Java 메서드를 호출할 수 있게 합니다.

흐름:
1. LLM에 툴(메서드)을 등록합니다
2. 사용자가 질문을 합니다
3. LLM이 어떤 툴을 어떤 인수로 호출할지 결정합니다
4. Spring AI가 메서드를 호출하고 결과를 LLM으로 다시 보냅니다
5. LLM이 툴 결과를 사용해 자연어 답변을 생성합니다

LLM은 코드를 직접 실행하지 않습니다 — Spring AI가 중간 역할을 합니다.

## @Tool 어노테이션

Spring AI 1.0에서는 `@Tool`로 어떤 메서드든 LLM이 사용할 수 있게 만들 수 있습니다:

```java
@Service
public class OrderTools {

    @Tool(description = "주문 ID로 주문의 현재 상태를 조회합니다")
    public OrderStatus getOrderStatus(
            @ToolParam(description = "주문 ID, 예: ORD-2024-001") String orderId) {

        // 실제 비즈니스 로직
        return orderRepository.findStatusById(orderId);
    }
}
```

### 내부에서 일어나는 일

Spring AI가 자동으로:
1. 메서드 시그니처에서 **JSON 스키마** 생성 (`String orderId` -> `{"type": "string"}`)
2. 스키마 + 설명을 사용 가능한 툴로 LLM에 전송
3. LLM이 툴 호출로 응답하면 인수를 역직렬화하고 메서드 호출
4. 반환 값을 최종 답변을 위해 LLM으로 다시 전송

이것은 `@RequestMapping`이 HTTP 요청을 메서드에 매핑하는 방식과 유사합니다 —
`@Tool`은 LLM 툴 호출을 메서드에 매핑합니다.

### @ToolParam

각 파라미터에 대한 더 많은 컨텍스트를 LLM에 제공하려면 `@ToolParam` 사용:

```java
@Tool(description = "특정 월의 매출을 조회합니다")
public Revenue getMonthlyRevenue(
        @ToolParam(description = "연도, 예: 2024") int year,
        @ToolParam(description = "월 (1-12)") int month) {
    // ...
}
```

설명은 JSON 스키마의 일부가 되어 LLM이 사용자의 자연어 입력에서
올바른 값을 추출할 수 있도록 합니다.

## ChatClient에 툴 등록

`ChatClient`에 툴 빈을 전달하세요:

```java
@RestController
public class ChatController {

    private final ChatClient chatClient;
    private final OrderTools orderTools;
    private final FaqSearchTool faqSearchTool;

    public ChatController(ChatClient.Builder builder,
                          OrderTools orderTools,
                          FaqSearchTool faqSearchTool) {
        this.chatClient = builder.build();
        this.orderTools = orderTools;
        this.faqSearchTool = faqSearchTool;
    }

    @PostMapping("/api/chat")
    public String chat(@RequestBody ChatRequest request) {
        return chatClient.prompt()
            .user(request.question())
            .tools(orderTools, faqSearchTool)  // 모든 툴 등록
            .call()
            .content();
    }
}
```

`.tools()` 메서드는 `@Tool` 어노테이션이 달린 메서드를 가진 어떤 빈도 받습니다.
Spring AI가 해당 빈의 모든 `@Tool` 메서드를 자동으로 발견합니다.

## @RequestMapping 비유

| 개념 | Spring MVC | Spring AI |
|---------|-----------|-----------|
| 요청 라우팅 | `@RequestMapping("/orders")` | `@Tool(description = "주문 상태 조회")` |
| 파라미터 추출 | `@PathVariable`, `@RequestParam` | `@ToolParam` |
| 스키마 생성 | OpenAPI / Swagger | LLM용 JSON 스키마 |
| 디스패처 | `DispatcherServlet` | `ToolCallingManager` |

`@Tool`을 HTTP 요청 대신 LLM 요청을 위한 `@RequestMapping`으로 생각하세요.

## 반환 타입

`@Tool` 메서드의 반환 값은 JSON으로 직렬화되어 LLM으로 전송됩니다.
record 또는 간단한 객체를 사용하세요:

```java
public record OrderStatus(
    String orderId,
    String status,           // "처리 중", "배송 중", "배송 완료"
    String trackingNumber,
    LocalDateTime updatedAt
) {}
```

LLM이 JSON을 받아 사람이 읽을 수 있는 답변으로 변환합니다.

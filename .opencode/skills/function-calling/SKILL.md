---
name: function-calling
description: Function Calling (@Tool), Reflection 패턴, 동적 도구 통합 가이드
---

# Function Calling Guide

## Function Calling이란?

LLM이 **외부 함수를 호출**하여 실시간 데이터를 가져오거나 작업을 수행하는 메커니즘입니다.
RAG가 "문서 검색"이라면, Function Calling은 "API 호출"입니다.

```
사용자: "주문 #12345 배송 상태는요?"

LLM 판단: "이건 문서 검색이 아니라 주문 API를 호출해야 해"
    |
    v
[getOrderStatus("12345")] → "배송 중, 내일 도착 예정"
    |
    v
LLM: "주문 #12345는 현재 배송 중이며 내일 도착 예정입니다."
```

## @Tool 어노테이션

Spring AI에서 LLM이 호출할 수 있는 함수를 정의합니다.

### 기본 사용

```java
@Component
public class OrderTools {

    @Tool(description = "주문 상태를 조회합니다. orderId는 'ORD-YYYY-NNN' 형식입니다.")
    public String getOrderStatus(
            @ToolParam(description = "조회할 주문 ID") String orderId) {
        // 실제 주문 시스템 API 호출 또는 모의 데이터
        return switch (orderId) {
            case "ORD-2024-001" -> "배송 중 (추적번호: 1Z999AA10123456784)";
            case "ORD-2024-002" -> "배송 완료 (2024-01-15 수령)";
            default -> "주문을 찾을 수 없습니다: " + orderId;
        };
    }

    @Tool(description = "특정 년월의 매출 데이터를 조회합니다.")
    public String getMonthlyRevenue(
            @ToolParam(description = "년도 (예: 2024)") int year,
            @ToolParam(description = "월 (1-12)") int month) {
        return "2024년 %d월 매출: $142,500.00".formatted(month);
    }
}
```

### FAQ 검색을 Tool로 래핑

```java
@Component
public class FaqSearchTool {

    private final RagService ragService;

    @Tool(description = "FAQ 문서에서 정보를 검색합니다. 제품 정책, 배송, 환불 등의 질문에 사용합니다.")
    public String searchFaq(
            @ToolParam(description = "검색할 질문") String query) {
        return ragService.search(query);
    }
}
```

## ChatClient에 Tool 연결

```java
@Autowired
private OrderTools orderTools;

@Autowired
private FaqSearchTool faqSearchTool;

String answer = chatClient.prompt()
    .user("주문 #ORD-2024-001 상태를 알려주세요")
    .tools(new MethodToolCallbackProvider(orderTools, faqSearchTool))
    .call()
    .content();
```

### 핵심: LLM이 자동으로 올바른 Tool을 선택

```
"주문 상태" → getOrderStatus 호출
"매출 데이터" → getMonthlyRevenue 호출
"환불 정책" → searchFaq 호출
```

수동 if/else 라우팅 없이 LLM이 Tool description을 보고 판단합니다.

## Tool 설명 작성 요령

Tool description이 정확해야 LLM이 올바른 Tool을 선택합니다.

```java
// Bad: 설명이 모호함
@Tool(description = "주문 조회")

// Good: 구체적이고 사용 조건 명시
@Tool(description = "주문 상태를 조회합니다. orderId는 'ORD-YYYY-NNN' 형식입니다. "
    + "배송 추적, 주문 상세 정보를 확인할 때 사용합니다.")
```

## Reflection 패턴

LLM이 자신의 결과를 검증하고, 부족하면 재시도하는 패턴입니다.

```
[1. 생성] → Tool 호출 → 결과 얻기
    |
    v
[2. 검증] → "이 결과가 사용자 질문에 실제로 답하나?"
    |
    ├── YES → 최종 답변 반환
    |
    └── NO → [3. 재시도] → 다른 방식 시도 (다른 Tool, 다른 파라미터)
```

### 구현

```java
@Service
public class ReflectionService {
    private final ChatClient chatClient;
    private final int maxRetries;

    public String askWithReflection(String question) {
        String answer = null;

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            // 1. 생성
            answer = chatClient.prompt()
                .user(question)
                .tools(toolProvider)
                .call()
                .content();

            // 2. 검증
            boolean isGood = chatClient.prompt()
                .system("다음 답변이 질문에 정확히 답하는지 YES/NO로 판단하세요.")
                .user("질문: %s\n답변: %s".formatted(question, answer))
                .call()
                .content()
                .contains("YES");

            if (isGood) {
                return answer;  // 통과
            }

            // 3. 재시도 (다음 루프)
            log.info("Reflection: 답변 부족, 재시도 {}/{}", attempt + 1, maxRetries);
        }

        return answer;  // 최대 재시도 후 최선의 답변 반환
    }
}
```

## 관련 스킬

- `spring-ai-api`: @Tool, ChatClient API 상세
- `multi-agent`: 에이전트 패턴 (Week 08)

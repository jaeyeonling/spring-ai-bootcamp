# M4 미션: Tool Use & MCP

## Stage 1에서 겪은 벽

FAQ와 정책 문서로는 답할 수 없는 질문이 있습니다:

> "제 주문 20240815-042 지금 어디 있어요?"
> "오늘 프로모션 중인 상품 있나요?"
> "반품 신청했는데 환불 금액이 얼마예요?"

이런 질문은 **실시간 데이터**가 필요합니다. 문서에는 없습니다.
RAG는 "문서에 있는 것"만 답할 수 있습니다.

---

## 미션

LLM이 적절한 함수를 자동으로 선택하여 호출하는 **Tool Use** 시스템을 구현하고,
동일한 Tool을 **MCP 프로토콜**로 외부에 노출하세요.

### 완료 기준

```
[ ] LLM이 질문을 보고 RAG vs Tool 중 자동으로 선택
[ ] 주문 조회 / 배송 추적 / 프로모션 조회 Tool 구현
[ ] Tool 실패 시 적절한 폴백 처리
[ ] MCP 서버로 동일 Tool 노출 (MCP 클라이언트에서 호출 가능)
[ ] Tool 라우팅 정확도 측정 (5개 시나리오 테스트)
```

---

## 핵심 개념

### Tool vs RAG — 언제 무엇을 쓰는가

| 구분 | RAG | Tool |
|------|-----|------|
| 데이터 성격 | 문서 (정적, 영속) | 실시간 데이터 (동적, 조회) |
| 예시 | 반품 정책, FAQ | 주문 상태, 재고, 프로모션 |
| 실패 시 | 관련 문서 없음 | 시스템 오류, 데이터 없음 |

### @Tool 어노테이션

```java
@Service
public class OrderTools {

    @Tool(description = "고객의 주문 상태를 조회합니다. 주문번호 형식: YYYYMMDD-NNN")
    public OrderStatus getOrderStatus(String orderId) {
        // 실제 주문 DB 조회 또는 Mock
        return orderService.findById(orderId);
    }

    @Tool(description = "배송 추적 정보를 조회합니다. 주문번호로 현재 위치와 예상 도착일 반환")
    public TrackingInfo trackDelivery(String orderId) {
        return deliveryService.track(orderId);
    }
}
```

### Tool Description이 핵심이다

LLM은 description을 읽고 어떤 Tool을 호출할지 결정합니다.
description이 모호하면 라우팅이 실패합니다.

나쁜 description:
```java
@Tool(description = "주문 조회")  // 너무 짧고 모호
```

좋은 description:
```java
@Tool(description = "고객의 특정 주문 상태를 실시간으로 조회합니다. "
    + "주문번호(형식: YYYYMMDD-NNN)가 있을 때 사용하세요. "
    + "배송 위치가 아닌 주문 처리 상태(결제완료/준비중/발송됨)를 반환합니다.")
```

### MCP 프로토콜

`@Tool` 메서드를 코드 변경 없이 MCP 서버로 노출할 수 있습니다:

```yaml
# application.yml
spring:
  ai:
    mcp:
      server:
        enabled: true
        name: cholog-faq-server
        transport: STDIO
```

```java
@SpringBootApplication
@EnableMcpServer  // 이것만 추가하면 @Tool이 자동으로 MCP Tool로 등록됨
public class Application { ... }
```

---

## 힌트

| 힌트 | 이럴 때 열어보세요 |
|------|-------------------|
| `HINT_01.md` | @Tool 등록과 ChatClient 연결 방법이 막힐 때 |
| `HINT_02.md` | Tool 라우팅이 제대로 안 될 때 (description 설계 가이드) |
| `HINT_03.md` | MCP 서버 설정 및 클라이언트 연결이 막힐 때 |

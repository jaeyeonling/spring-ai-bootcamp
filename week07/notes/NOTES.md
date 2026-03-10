# Week 07 — Function Calling: @Tool, Reflection 패턴

> **미션**: FAQ 검색뿐 아니라 주문 API, 매출 API 등 실시간 데이터도 조회해야 한다.
> LLM이 수동 if/else 없이 질문 의도를 파악해 자동으로 올바른 툴을 호출하게 하라.

---

## 구현 파일 구조

```
week07/src/main/java/com/jaeyeonling/week07/
├── OrderTools.java        — @Tool: getOrderStatus, getMonthlyRevenue (모의 데이터)
├── FaqSearchTool.java     — @Tool: searchFaq (VectorStore RAG 래핑)
├── ChatController.java    — REST 엔드포인트 (/api/chat, /api/chat/verified)
├── ReflectionService.java — 생성 → 검증 → 재시도 루프
└── Week07Application.java — 진입점
```

---

## 왜 Function Calling인가

Week 01~06까지는 RAG 파이프라인이 항상 벡터 검색을 먼저 실행했다.
모든 질문이 문서 검색으로 해결될 수 있다고 가정했다.

```
Week 01~06: 질문 → 벡터 검색 → LLM 답변  (항상 같은 경로)

Week 07:    질문 → LLM이 판단 → 주문 API  호출
                             → 매출 API  호출
                             → 벡터 검색 호출
                             → 여러 툴 복합 호출
```

핵심 전환: **LLM이 라우터가 된다.** 수동 분기 없이 툴 설명(description)만 보고 결정.

---

## Spring AI @Tool 동작 원리

```
1. @Tool 어노테이션된 메서드가 있는 빈
        │
        ▼
2. chatClient.prompt().tools(orderTools, faqSearchTool) 등록
        │
        ▼
3. Spring AI가 메서드 시그니처 + description을 OpenAI Function Calling 스키마로 변환
        │
        ▼
4. LLM이 질문을 보고 어떤 함수를 호출할지 결정 → JSON 인수 생성
        │
        ▼
5. Spring AI가 JSON 인수를 역직렬화해서 실제 Java 메서드 호출
        │
        ▼
6. 결과를 LLM에게 다시 전달 → 최종 답변 생성
```

**중요**: `@ToolParam(description = "...")` 설명이 LLM의 인수 추출 정확도에 직접 영향을 준다.
"주문 ID, 예: ORD-2024-001"처럼 예시를 포함할수록 정확도가 높아진다.

---

## 내부 동작: HTTP 레벨에서 무슨 일이 일어나는가

Spring AI가 추상화해주지만, 실제로는 OpenAI와 두 번 왕복한다.

### 1라운드: LLM이 툴 호출을 결정

```
클라이언트 → OpenAI API (1차 요청)

POST /v1/chat/completions
{
  "model": "gpt-4o-mini",
  "messages": [
    {"role": "system", "content": "당신은 도움이 되는 고객 지원 담당자입니다..."},
    {"role": "user",   "content": "주문 #ORD-2024-001의 배송 상태는요?"}
  ],
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "getOrderStatus",
        "description": "주문 ID로 고객 주문의 현재 상태를 조회합니다...",
        "parameters": {
          "type": "object",
          "properties": {
            "orderId": {
              "type": "string",
              "description": "주문 ID, 예: ORD-2024-001"
            }
          },
          "required": ["orderId"]
        }
      }
    },
    { ... faqSearchTool 스키마 ... }
  ]
}
```

OpenAI 응답 (`finish_reason: "tool_calls"`):

```json
{
  "choices": [{
    "finish_reason": "tool_calls",
    "message": {
      "role": "assistant",
      "tool_calls": [{
        "id": "call_abc123",
        "type": "function",
        "function": {
          "name": "getOrderStatus",
          "arguments": "{\"orderId\": \"ORD-2024-001\"}"
        }
      }]
    }
  }]
}
```

### Spring AI의 역할 (사이)

`finish_reason: "tool_calls"`를 감지하면:
1. `arguments` JSON을 역직렬화 → `orderId = "ORD-2024-001"`
2. Java 리플렉션으로 `orderTools.getOrderStatus("ORD-2024-001")` 호출
3. 반환값을 JSON으로 직렬화

### 2라운드: 툴 결과를 포함해 최종 답변 생성

```
클라이언트 → OpenAI API (2차 요청)

POST /v1/chat/completions
{
  "messages": [
    {"role": "system",    "content": "..."},
    {"role": "user",      "content": "주문 #ORD-2024-001의 배송 상태는요?"},
    {"role": "assistant", "tool_calls": [{ "id": "call_abc123", ... }]},
    {"role": "tool",      "tool_call_id": "call_abc123",
                          "content": "{\"orderId\":\"ORD-2024-001\",\"status\":\"배송 중\",\"trackingNumber\":\"1Z999...\"}"}
  ]
}
```

OpenAI 응답 (`finish_reason: "stop"`):

```json
{
  "choices": [{
    "finish_reason": "stop",
    "message": {
      "role": "assistant",
      "content": "주문 #ORD-2024-001은 현재 배송 중 상태입니다. 추적번호는 1Z999AA10123456784입니다."
    }
  }]
}
```

### 전체 흐름 요약

```
사용자 질문
    │
    ▼
[1차 API 호출] → LLM: "getOrderStatus를 orderId=ORD-2024-001로 호출해야 함"
    │
    ▼
[Spring AI] → Java 메서드 실제 호출 → OrderStatus 반환
    │
    ▼
[2차 API 호출] → LLM: 툴 결과를 자연어로 요약
    │
    ▼
최종 답변
```

**복수 툴 호출**: LLM이 한 번에 여러 툴을 호출할 수도 있다 (`tool_calls` 배열에 여러 항목).
Spring AI는 이를 병렬로 실행하고 모든 결과를 2차 요청에 포함한다.

**비용 함의**: 툴이 있는 요청은 최소 2회 API 호출 = 최소 2배 토큰 비용.
Reflection까지 추가하면 최대 `(maxRetries + 1) × 2 + (maxRetries + 1)` 호출이 발생한다.

---

## 구현 흐름

### FaqSearchTool.searchFaq()

기존 RAG 파이프라인을 @Tool로 노출하는 것이 핵심이다.
VectorStore.similaritySearch()를 그대로 호출한다.

```
searchFaq(query)
    │
    ▼
vectorStore.similaritySearch(
    SearchRequest.builder().query(query).topK(3).build())
    │
    ├─ 결과 없음 → "관련 FAQ 항목을 찾을 수 없습니다: {query}"
    └─ 결과 있음 → 문서들을 "\n\n---\n\n"으로 이어 붙여 반환
```

### ChatController

두 엔드포인트:

```
POST /api/chat
    chatClient.prompt()
        .system("...")
        .user(question)
        .tools(orderTools, faqSearchTool)
        .call()
        .content()
    → ChatResponse(answer, verified=false)

POST /api/chat/verified
    reflectionService.chatWithReflection(question, orderTools, faqSearchTool)
    → ChatResponse(answer, verified=true)
```

### ReflectionService

생성 → 검증 → 재시도 루프:

```
for attempt in 0..maxRetries:
    │
    ▼
1. 생성: chatClient + tools → answer
    │
    ▼
2. 검증: chatClient (툴 없음) + VERIFICATION_PROMPT
         "질문: {question}\n답변: {answer}"
         → "PASS" 또는 "FAIL: {reason}"
    │
    ├─ "PASS" → 즉시 반환
    └─ "FAIL" → 로그 남기고 재시도

maxRetries 소진 → 마지막 answer 반환
```

**verify()**: 루프 없이 단일 검증만 수행. 테스트 독립 실행에 활용.

---

## 툴 라우팅 동작 예측

| 질문 | LLM이 선택할 툴 | 근거 |
|------|--------------|------|
| "주문 #ORD-2024-001의 배송 상태는요?" | `getOrderStatus` | "주문 ID로 상태 조회" 설명 매칭 |
| "2024년 1월 매출은 얼마인가요?" | `getMonthlyRevenue` | "특정 월의 총 매출 조회" 설명 매칭 |
| "환불 정책이 어떻게 되나요?" | `searchFaq` | "특정 주문/매출이 아닌" 조건 매칭 |
| "30일 후에 반품할 수 있나요?" | `searchFaq` | 정책 질문 → FAQ 검색 |
| "주문 #ORD-2024-002 상세 정보" | `getOrderStatus` | 주문 ID 포함 → 주문 툴 |

`searchFaq`의 description에 "특정 주문 상태나 매출 수치에 관한 질문이 아닐 때 사용하세요"가 포함되어 있어 명확하게 구분된다.

---

## 예상 트러블슈팅

### 1. VectorStore 컬렉션 비어있음

week07-faq 컬렉션에 데이터가 없으면 searchFaq가 항상 빈 결과를 반환한다.
Week 05처럼 FaqIngestionService를 별도로 만들거나, week06-faq 컬렉션을 재사용해야 할 수 있다.

### 2. @Disabled 테스트

`ToolCallingTest`에 `@Disabled` 어노테이션이 달려있다. 구현 완료 후 제거해야 테스트가 실행된다.

### 3. Reflection 검증 프롬프트 엄격도

VERIFICATION_PROMPT가 너무 엄격하면 올바른 답변도 FAIL 처리될 수 있다.
"찾을 수 없음"을 항상 FAIL로 처리하는 규칙이 있어서, 모의 데이터에 없는 주문 ID를 조회하면 재시도가 발생할 수 있다.

---

## 트러블슈팅

### 1. VectorStore 컬렉션 — week07-faq

week07-faq 컬렉션이 비어있어도 `searchFaq`는 "관련 FAQ 항목을 찾을 수 없습니다" 메시지를 반환하고 테스트는 통과한다. 단, 실제 FAQ 내용이 필요한 환경에서는 데이터를 인제스트해야 한다.

### 2. @Disabled 제거

`ToolCallingTest`의 `@Disabled` 어노테이션을 제거해야 테스트가 실행된다. 구현 완료 후 제거했다.

---

## 테스트 결과 (7/7 통과)

툴 라우팅 로그 (실제 관찰):

```
툴 호출됨: searchFaq(query=반품 정책)       ← "30일 후에도 반품할 수 있나요?"
툴 호출됨: searchFaq(query=반품)
툴 호출됨: searchFaq(query=환불 정책)       ← "환불 정책이 어떻게 되나요?"
(getOrderStatus 호출)                      ← "주문 #ORD-2024-001 상태는?"
(getOrderStatus 호출)                      ← "주문 #ORD-2024-002 상세 정보"
(getMonthlyRevenue 호출)                   ← "2024년 1월 매출은?"
```

- [x] `shouldCallGetOrderStatusForOrderQuestions` — "배송 중" 포함
- [x] `shouldCallGetMonthlyRevenueForRevenueQuestions` — "142" 포함
- [x] `shouldCallSearchFaqForPolicyQuestions` — 환불/반품/refund/return 포함
- [x] `shouldCallSearchFaqForReturnQuestions` — 20자 이상 응답
- [x] `shouldCallGetOrderStatusForOrderDetailsQuestions` — "처리 중" 포함
- [x] `reflectionShouldVerifyCorrectAnswers` — verify() → true
- [x] `reflectionShouldRejectWrongAnswers` — verify() → false

---

## 배운 것 / 느낀 것

1. **툴 description이 라우터다.** `searchFaq`의 description에 "특정 주문 상태나 매출 수치에 관한 질문이 아닐 때 사용하세요"를 명시하는 것만으로 LLM이 정확하게 구분했다. if/else 코드가 아닌 자연어 설명이 라우팅 로직이 된다.

2. **Function Calling은 최소 2번 왕복이다.** 1차 호출에서 LLM이 어떤 툴을 쓸지 결정하고, 2차 호출에서 툴 결과를 포함해 최종 답변을 생성한다. 이 사실을 모르면 응답 지연이 2배인 이유를 이해하기 어렵다.

3. **Reflection은 생각보다 실용적이다.** VERIFICATION_PROMPT에서 "찾을 수 없음은 FAIL"이라는 규칙 하나로, 모의 데이터에 없는 주문 ID 조회 시 자동으로 재시도가 발생한다. 별도 예외 처리 코드 없이 LLM이 품질 게이트 역할을 한다.

4. **RAG가 툴의 하나로 수렴한다.** Week 01~06에서 RAG는 파이프라인의 중심이었다. Week 07에서 RAG는 여러 툴 중 하나(`searchFaq`)가 됐다. 아키텍처의 무게 중심이 "검색"에서 "에이전트"로 이동하는 전환점이다.

---

## 다음 주차로 연결

Week 08에서 이어지는 것:
- Week 07의 단일 에이전트(ChatController)가 Week 08에서 멀티 에이전트로 확장됨
- `@Tool`로 노출된 각 도구가 별도 Worker 에이전트로 분리될 수 있음
- Reflection 패턴이 Orchestrator-Worker 패턴의 "검증" 역할과 연결됨

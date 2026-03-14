# Week 09 — MCP 서버: 표준화된 도구 인터페이스

> **미션**: FAQ 검색과 주문 조회 기능을 MCP 서버로 노출하여,
> Claude Desktop/Cursor/MCP Inspector 등 MCP 호환 클라이언트에서 직접 사용할 수 있게 하라.

---

## 구현 파일 구조

```
week09/src/main/java/com/jaeyeonling/week09/
├── FaqMcpService.java       — @Tool: searchFaq, faqCategories, customerSupportPrompt
├── OrderMcpService.java     — @Tool: lookupOrder, checkOrderStatus
├── McpToolConfig.java       — MethodToolCallbackProvider 빈 등록
├── FaqIngestionService.java — week09-faq 컬렉션 ETL
└── Week09Application.java   — 진입점
```

---

## 왜 MCP인가

Week 07~08에서 만든 도구들(@Tool)은 Spring Boot 웹 앱 내부에서만 사용 가능했다.
REST API를 만들면 사용자가 문서를 읽고, 엔드포인트를 호출하는 코드를 작성해야 한다.

MCP(Model Context Protocol)는 이 문제를 해결한다:

```
REST API:
  개발자가 문서 읽기 → HTTP 호출 작성 → 결과 파싱 → 사용

MCP 서버:
  AI 클라이언트가 자동 발견 → 툴 설명 읽기 → 자동 호출 → 결과 사용
```

핵심 전환: **사용자(개발자)가 연결 코드를 작성**하는 대신, **AI가 프로토콜로 직접 호출**한다.

---

## MCP 프로토콜 내부 동작

### 3가지 프리미티브

| 프리미티브 | REST 비유 | 역할 | 구현 방법 |
|-----------|----------|------|---------|
| **Tools** | POST 엔드포인트 | AI가 호출하는 액션 | `@Tool` 메서드 |
| **Resources** | GET 엔드포인트 | AI가 읽는 데이터 | `@Tool`로 구현 (목록 반환) |
| **Prompts** | 템플릿 | AI가 사용하는 프롬프트 | `@Tool`로 구현 (템플릿 반환) |

Spring AI 1.1.2에서는 세 프리미티브 모두 `@Tool` 어노테이션으로 구현한다.

### 전송 모드

```
STDIO (기본):
  Claude Desktop ←→ stdin/stdout ←→ MCP 서버 (Java 프로세스)
  - Claude Desktop이 java -jar로 서버를 직접 실행
  - JSON-RPC 메시지를 stdin/stdout으로 교환
  - 가장 단순한 설정

HTTP/SSE:
  MCP 클라이언트 ←→ HTTP ←→ MCP 서버 (웹 서버)
  - spring-ai-starter-mcp-server-webmvc 사용
  - Server-Sent Events로 스트리밍
  - 원격 서버에 적합
```

### 등록 플로우

```
1. @Tool 메서드가 있는 @Service 빈
        │
        ▼
2. McpToolConfig에서 MethodToolCallbackProvider.builder()
        .toolObjects(faqMcpService, orderMcpService).build()
        │
        ▼
3. Spring AI MCP Starter가 ToolCallbackProvider 빈을 감지
        │
        ▼
4. MCP 서버가 시작되며 tools/list 응답에 등록
        │
        ▼
5. MCP 클라이언트가 tools/list 호출 → 도구 목록 자동 발견
        │
        ▼
6. AI가 도구 설명을 보고 적절한 도구를 선택/호출
```

### JSON-RPC 프로토콜 예시

클라이언트 → 서버 (tools/list):
```json
{"jsonrpc": "2.0", "method": "tools/list", "id": 1}
```

서버 → 클라이언트 (응답):
```json
{
  "jsonrpc": "2.0", "id": 1,
  "result": {
    "tools": [
      {
        "name": "searchFaq",
        "description": "자연어 쿼리로 FAQ 문서를 검색합니다...",
        "inputSchema": {
          "type": "object",
          "properties": {
            "query": {"type": "string", "description": "자연어 검색 쿼리"},
            "topK": {"type": "integer", "description": "반환할 최대 결과 수"}
          },
          "required": ["query", "topK"]
        }
      }
    ]
  }
}
```

클라이언트 → 서버 (tools/call):
```json
{
  "jsonrpc": "2.0", "method": "tools/call", "id": 2,
  "params": {
    "name": "searchFaq",
    "arguments": {"query": "환불 정책", "topK": 3}
  }
}
```

---

## Week 07 @Tool과의 차이

| 측면 | Week 07 @Tool | Week 09 MCP @Tool |
|------|-------------|-----------------|
| 호출자 | 같은 프로세스 내 ChatClient | 외부 MCP 클라이언트 |
| 전송 | 메서드 호출 (in-process) | JSON-RPC (stdio/HTTP) |
| 발견 | 코드에 직접 등록 | 프로토콜로 자동 발견 |
| 사용처 | Spring Boot 앱 내부 | Claude Desktop, Cursor, 모든 MCP 클라이언트 |
| 코드 변경 | `@Tool` + `ToolCallbackProvider` | 동일 (프로토콜 레이어만 추가) |

핵심: **Java 코드 레벨에서 `@Tool` 사용법은 동일**하다. MCP는 전송 레이어만 추가한다.

---

## 구현 계획

### FaqMcpService

3가지 프리미티브 역할:

```
searchFaq(query, topK)     — Tool 역할: FAQ 검색 액션
faqCategories()            — Resource 역할: FAQ 카테고리 목록 (읽기 전용 데이터)
customerSupportPrompt()    — Prompt 역할: 고객지원 프롬프트 템플릿
```

### OrderMcpService

```
lookupOrder(orderId)       — Tool 역할: 주문 상세 조회
checkOrderStatus(orderId)  — Tool 역할: 주문 상태 간략 확인
```

### McpToolConfig

`MethodToolCallbackProvider` 빈 등록. `@Tool`만 달아서는 MCP 서버에 자동 등록되지 않는다.

### FaqIngestionService

week09-faq 컬렉션에 FAQ 데이터를 적재. Week 05/07과 동일 패턴.

---

## 예상 트러블슈팅

### 1. MethodToolCallbackProvider 빈 누락

`@Tool`만 달면 Spring 빈으로는 등록되지만 MCP 서버에는 노출되지 않는다.
반드시 `MethodToolCallbackProvider.builder().toolObjects(service).build()`를 빈으로 등록해야 한다.

### 2. web-application-type: none

STDIO 모드에서는 웹 서버가 불필요하다. `spring.main.web-application-type: none` 설정이 있으면
`@SpringBootTest`에서 웹 컨텍스트가 뜨지 않는다. 테스트에서 MCP 전송 레이어는 테스트하지 않고
`@Tool` 메서드를 직접 호출하는 방식으로 검증한다.

### 3. Qdrant 컬렉션 비어있음

week09-faq 컬렉션에 데이터가 없으면 searchFaq가 빈 결과를 반환한다.
FaqIngestionService로 데이터를 적재해야 한다.

### 4. VectorStore 빈 생성 실패

Qdrant가 실행 중이지 않으면 `QdrantVectorStore` 빈 생성에 실패한다.
`docker run -d -p 6333:6333 -p 6334:6334 qdrant/qdrant`로 먼저 시작해야 한다.

---

## 트러블슈팅

### 1. MCP Annotation Scanner가 @Tool을 자동 발견

로그에서 `McpServerAnnotationScannerAutoConfiguration`이 동작하는 것을 확인했다.
Spring AI MCP Starter가 `@Tool` 어노테이션이 붙은 빈을 자동으로 스캔하여 MCP 서버에 등록한다.
우리가 만든 `McpToolConfig`의 `MethodToolCallbackProvider`는 `"No tool methods found"` 경고가 뜨지만,
Annotation Scanner가 별도 경로로 5개 도구를 모두 등록했다 (`Registered tools: 5`).

이는 Spring AI 1.1.2의 MCP Starter가 두 가지 등록 경로를 가지고 있음을 의미한다:
- 경로 A: `MethodToolCallbackProvider` 빈 → `SyncMcpToolProvider`
- 경로 B: `McpServerAnnotationScanner` → `@Tool` 자동 발견

Week 07에서는 MCP Starter가 없어서 `MethodToolCallbackProvider`가 필수였지만,
Week 09에서는 MCP Starter의 Annotation Scanner가 자동으로 `@Tool`을 발견한다.

### 2. spring-ai-tika-document-reader 의존성 추가

FaqIngestionService에서 TikaDocumentReader를 사용하므로 build.gradle에
`spring-ai-tika-document-reader` 의존성을 추가했다. Week 07과 동일한 패턴.

### 3. 테스트에서 MCP 전송 레이어 검증 불가

`spring.main.web-application-type: none` + STDIO 모드이므로 테스트에서는 MCP 프로토콜 레이어를
직접 테스트할 수 없다. `@Tool` 메서드를 일반 Java 메서드로 직접 호출하여 비즈니스 로직만 검증했다.
실제 MCP 프로토콜 검증은 MCP Inspector로 수동 테스트해야 한다.

---

## 테스트 결과 (9/9 통과)

```
McpServerTest > FaqMcpService가 주입 가능하다                         PASSED
McpServerTest > OrderMcpService가 주입 가능하다                       PASSED
McpServerTest > MCP 서버 설정으로 애플리케이션 컨텍스트가 로드된다      PASSED
McpServerTest > searchFaq가 유효한 쿼리에 대해 결과를 반환한다         PASSED
McpServerTest > faqCategories가 카테고리 목록을 반환한다               PASSED
McpServerTest > customerSupportPrompt가 프롬프트 템플릿을 반환한다     PASSED
McpServerTest > lookupOrder가 알려진 주문 ID에 대해 상세 데이터를 반환한다 PASSED
McpServerTest > lookupOrder가 알 수 없는 주문 ID에 대해 에러를 반환한다   PASSED
McpServerTest > checkOrderStatus가 상태와 예상 배송일을 반환한다       PASSED

BUILD SUCCESSFUL in 11s
```

MCP 서버 등록 상태:
- Registered tools: 5 (searchFaq, faqCategories, customerSupportPrompt, lookupOrder, checkOrderStatus)
- ETL: 17개 FAQ 청크 적재 완료
- 전송 모드: STDIO

---

## 배운 것 / 느낀 것

1. **MCP는 전송 레이어만 추가한다.** Week 07의 `@Tool` 코드와 거의 동일하다. `spring-ai-starter-mcp-server` 의존성 하나와 `application.yml` 설정 몇 줄이 전부다. Java 코드 레벨에서 달라지는 것이 없다.

2. **Annotation Scanner의 존재.** MCP Starter가 `@Tool` 빈을 자동 발견해서 등록하므로, 사실 `McpToolConfig`의 `MethodToolCallbackProvider`가 없어도 도구가 등록된다. 하지만 명시적 등록이 교육적으로 더 명확하다.

3. **3가지 프리미티브의 구분이 모호하다.** Spring AI 1.1.2에서는 Resources, Prompts 모두 `@Tool`로 구현한다. 프로토콜 수준에서는 구분이 있지만(tools/list, resources/list, prompts/list), Java 구현에서는 모두 같은 어노테이션이다. 구분은 의미론적(description으로 역할 전달)이다.

4. **STDIO vs HTTP/SSE의 트레이드오프.** STDIO는 Claude Desktop처럼 로컬에서 프로세스를 직접 실행하는 클라이언트에 적합하다. 원격 서버 배포에는 HTTP/SSE가 필요하며 `spring-ai-starter-mcp-server-webmvc`로 전환해야 한다.

---

## 다음 주차로 연결

Week 10에서 이어지는 것:
- Week 09의 MCP 서버 + Week 07의 @Tool + Week 08의 멀티 에이전트를 전체 통합
- Docker Compose로 프로덕션 배포
- 전체 스택 (Qdrant + Ollama + Spring Boot + MCP) 통합

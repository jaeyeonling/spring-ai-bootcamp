# Week 09 — MCP 서버 평가: REST API vs MCP 프로토콜

## 배경

Week 07~08에서 만든 FAQ 검색, 주문 조회, 멀티 에이전트 기능이 Spring Boot 웹 앱 내부에서만 사용 가능하다. 사용자가 IDE(Cursor)나 AI 클라이언트(Claude Desktop)에서 직접 사용하려면 별도 웹 앱을 열어야 한다.

MCP(Model Context Protocol)는 AI 클라이언트가 단일 프로토콜로 외부 도구를 자동 발견하고 호출할 수 있게 하는 개방형 표준이다. REST API 대비 MCP의 장단점을 평가한다.

---

## 구현 결과

### 등록된 MCP 도구 (5개)

| 도구 | 프리미티브 역할 | 파라미터 | Week 07 대응 |
|------|-------------|---------|-------------|
| `searchFaq` | Tool | query, topK | `FaqSearchTool.searchFaq` |
| `faqCategories` | Resource | 없음 | 신규 |
| `customerSupportPrompt` | Prompt | issue | 신규 |
| `lookupOrder` | Tool | orderId | `OrderTools.getOrderStatus` |
| `checkOrderStatus` | Tool | orderId | `OrderTools.getOrderStatus` (경량) |

### 코드 변경량

Week 07 → Week 09로 MCP 서버를 만들기 위해 추가/변경한 코드:

| 파일 | 변경 |
|------|------|
| `build.gradle` | `spring-ai-starter-mcp-server` + `spring-ai-tika-document-reader` 의존성 2줄 |
| `application.yml` | `spring.ai.mcp.server` 설정 3줄 + `web-application-type: none` 1줄 |
| `FaqMcpService.java` | `@Tool` 메서드 3개 (Week 07 FaqSearchTool과 거의 동일) |
| `OrderMcpService.java` | `@Tool` 메서드 2개 (Week 07 OrderTools와 거의 동일) |
| `McpToolConfig.java` | `MethodToolCallbackProvider` 빈 1개 |
| `FaqIngestionService.java` | Week 07과 동일 복사 |

**Java 코드 레벨에서 `@Tool` 사용법은 Week 07과 동일하다.** MCP는 전송 레이어만 추가한다.

---

## REST API vs MCP 비교

### 개발자 경험

| 측면 | REST API | MCP 서버 |
|------|---------|---------|
| **도구 발견** | Swagger/문서를 읽고 엔드포인트 찾기 | 클라이언트가 `tools/list`로 자동 발견 |
| **클라이언트 코드** | HTTP 호출, 요청/응답 파싱 작성 | AI가 직접 호출 (코드 0줄) |
| **파라미터 설명** | OpenAPI spec 별도 작성 | `@ToolParam(description=...)` 인라인 |
| **새 도구 추가** | 엔드포인트 + 문서 + 클라이언트 코드 | `@Tool` 메서드 1개 |
| **호출자** | 개발자가 작성한 코드 | AI 모델 |

### 인프라

| 측면 | REST API | MCP 서버 (STDIO) | MCP 서버 (HTTP/SSE) |
|------|---------|----------------|-------------------|
| **전송** | HTTP | stdin/stdout | HTTP + SSE |
| **배포** | 웹 서버 필요 | 로컬 프로세스 | 웹 서버 필요 |
| **인증** | API 키, OAuth 등 | 프로세스 레벨 격리 | 프로토콜 내장 |
| **확장성** | 수평 스케일링 가능 | 단일 프로세스 | 수평 스케일링 가능 |
| **모니터링** | HTTP 메트릭 | 프로세스 모니터링 | HTTP 메트릭 |

### 비용

MCP 서버 자체에 추가 비용은 없다. 도구 호출 시 발생하는 비용은 Week 07과 동일하다.

| 항목 | REST API | MCP |
|------|---------|-----|
| 개발 비용 | 엔드포인트 + 문서 + 클라이언트 코드 | `@Tool` 메서드만 |
| 운영 비용 | 동일 | 동일 |
| API 호출 비용 | 동일 | 동일 (LLM이 호출) |

---

## MCP 프리미티브 평가

### Tool — 적합

`searchFaq`, `lookupOrder`, `checkOrderStatus`는 AI가 호출하는 액션이다.
파라미터를 받고, 실행하고, 결과를 반환한다. Tool 프리미티브의 정확한 용도.

### Resource — 부분 적합

`faqCategories`는 읽기 전용 데이터를 반환한다. 개념적으로는 Resource(`resource://faq/categories`)이지만,
Spring AI 1.1.2에서는 `@Tool`로 구현해야 하므로 Tool로 등록된다.
MCP 클라이언트 입장에서는 Tool과 Resource의 구분이 description으로만 가능하다.

### Prompt — 부분 적합

`customerSupportPrompt`는 프롬프트 템플릿을 반환한다. 개념적으로는 Prompt 프리미티브이지만,
역시 `@Tool`로 구현된다. AI 클라이언트가 "이 도구를 호출하면 프롬프트를 준다"고 이해해야 하는데,
description으로 역할을 전달하는 것은 약간 우회적이다.

---

## BEFORE / AFTER

### BEFORE — Week 07 REST API 내장

| 항목 | 수치 |
|------|------|
| 아키텍처 | Spring Boot 웹 앱 내 `@Tool` |
| 사용 방법 | 브라우저/curl로 REST 엔드포인트 호출 |
| 도구 발견 | 문서 읽기 또는 코드 읽기 |
| 새 도구 추가 | `@Tool` + Controller + 문서 |
| 사용 가능 클라이언트 | HTTP 클라이언트만 |

### AFTER — Week 09 MCP 서버

| 항목 | 수치 |
|------|------|
| 아키텍처 | Spring Boot MCP 서버 (STDIO) |
| 사용 방법 | Claude Desktop/Cursor에서 자동 발견 + 호출 |
| 도구 발견 | `tools/list` 프로토콜로 자동 |
| 새 도구 추가 | `@Tool` 메서드 1개 |
| 사용 가능 클라이언트 | 모든 MCP 호환 클라이언트 |
| 등록된 도구 | 5개 |
| ETL | 17개 FAQ 청크 |

---

## 결론

### MCP가 REST를 대체하는가

아니다. 상호 보완적이다:

- **REST API**: 프로그래밍 방식 통합 (서비스 간 호출, 프론트엔드 연결)
- **MCP 서버**: AI 클라이언트 통합 (Claude Desktop, Cursor, 모든 MCP 호환 도구)

같은 `@Tool` 메서드가 Week 07에서는 ChatClient가 호출하고, Week 09에서는 MCP 프로토콜로 외부 AI가 호출한다. 코드는 동일하고 전송 레이어만 다르다.

### 권장 전략

| 시나리오 | 권장 |
|---------|------|
| 내부 서비스 간 통합 | REST API |
| AI 클라이언트 연동 (Claude, Cursor) | MCP 서버 |
| 둘 다 필요 | 같은 `@Tool` 메서드를 REST + MCP 양쪽에 노출 |
| 원격 배포 | MCP HTTP/SSE 모드 (`spring-ai-starter-mcp-server-webmvc`) |
| 로컬 개발 도구 | MCP STDIO 모드 (현재 구현) |

---

## 관련 문서

- [구현 노트](notes/NOTES.md)
- [Week 07 Function Calling 평가](../week07/EVALUATION.md)
- [Week 10 전체 통합](../week10/mission/MISSION.md)

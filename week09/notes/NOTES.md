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

## 트러블슈팅

### 1. STDIO 클라이언트 연결 실패 — `bootRun` 대신 `java -jar`

`gradlew :week09:bootRun`을 MCP command로 등록하면 클라이언트가 Disabled 처리된다.
Gradle은 JVM을 자식 프로세스로 띄우므로 stdin이 포워딩되지 않아 `initialize` 요청이 서버에 도달하지 않는다.

**해결**: fat jar 빌드 후 `java -jar`로 직접 실행. 환경변수 전달 문제도 함께 해소된다.

```bash
./gradlew :week09:bootJar
exec java -jar week09/build/libs/week09-0.0.1-SNAPSHOT.jar
```

### 2. 서버 시작 성공인데 JSON-RPC 무응답 — `stdio=true` 누락

`spring.main.web-application-type: none`은 웹 서버를 끄는 것이지 STDIO transport를 켜는 게 아니다.
`Registered tools: 5`와 `Started` 로그가 나와도 응답이 없다면 이 설정이 빠진 것이다.

```yaml
spring.ai.mcp.server.stdio: true  # 반드시 명시
```

### 3. 배너/콘솔 로그가 stdout 오염 — 공식 설정 그대로 사용

`StdioServerTransportProvider`는 `System.out`으로 JSON-RPC를 쓴다. 배너와 로그가 같은 스트림을 쓰면 파싱 실패한다.

```properties
spring.main.banner-mode=off
logging.pattern.console=          # 빈 값 = 콘솔 출력 비활성화 (빈 줄도 나오지 않음)
logging.file.name=/tmp/week09-mcp-server.log  # 로그는 파일로
```

### 4. MCP Annotation Scanner가 `@Tool`을 이중 등록

`MethodToolCallbackProvider` 빈을 만들면 `"No tool methods found"` 경고가 뜨지만 도구는 정상 등록된다.
MCP Starter의 Annotation Scanner가 `@Tool` 빈을 별도 경로로 자동 발견하기 때문이다.
Week 07과 달리 Week 09에서는 `McpToolConfig` 없이도 동작한다.

### 5. 테스트에서 MCP 전송 레이어 검증 불가

STDIO 모드에서는 `@Tool` 메서드를 직접 호출하여 비즈니스 로직만 검증했다.
MCP 프로토콜 레이어 검증은 MCP Inspector 또는 위 `java -jar` 방식으로 수동 확인.

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

1. **MCP는 전송 레이어만 추가한다.** Week 07의 `@Tool` 코드와 거의 동일하다. `spring-ai-starter-mcp-server` 의존성 하나와 `application.yml` 설정 몇 줄이 전부다.

2. **Annotation Scanner가 `@Tool` 빈을 자동 발견한다.** `McpToolConfig` 없이도 도구가 등록된다. 명시적 등록은 의도를 드러내기 위한 것이지 필수가 아니다.

3. **3가지 프리미티브의 구분이 모호하다.** Spring AI 1.1.2에서는 Resources, Prompts 모두 `@Tool`로 구현한다. 구분은 `description`으로 전달하는 의미론적 역할에 있다.

4. **STDIO는 `java -jar` + `stdio=true` 조합이 필수다.** `bootRun`은 stdin 미전달, `stdio=true` 누락은 무응답. 둘 다 서버 로그상 정상 시작처럼 보여서 원인 파악이 어렵다.

5. **STDIO vs HTTP/SSE.** STDIO는 로컬 클라이언트 전용. 원격 배포는 `spring-ai-starter-mcp-server-webmvc`로 전환해야 한다.

---

## 다음 주차로 연결

Week 10에서 이어지는 것:
- Week 09의 MCP 서버 + Week 07의 @Tool + Week 08의 멀티 에이전트를 전체 통합
- Docker Compose로 프로덕션 배포
- 전체 스택 (Qdrant + Ollama + Spring Boot + MCP) 통합

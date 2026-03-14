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

### 1. OpenCode MCP 클라이언트 연결 — STDIO 서버의 4가지 함정

Week 09 구현 후 OpenCode에 MCP 서버를 등록할 때 Disabled 상태에서 빠져나오지 못하는 문제가 발생했다.
원인을 하나씩 제거하며 해결했다.

#### 함정 1: `bootRun`은 stdin을 MCP 프로세스에 전달하지 않는다

`opencode.json`에 `gradlew :week09:bootRun`을 command로 등록했는데 Disabled가 반복됐다.

```
MCP 클라이언트 → stdin → [Gradle bootRun] → [JVM 자식 프로세스]
                                   ↑
                       Gradle이 stdin을 가로채서 JVM에 전달하지 않음
```

Gradle `bootRun`은 JVM을 자식 프로세스로 실행하며 stdin을 포워딩하지 않는다.
MCP 클라이언트가 보내는 `initialize` 요청이 Spring 서버에 도달하지 않아서 응답이 없고 타임아웃으로 Disabled 처리된다.

**해결**: `bootJar`로 fat jar를 빌드한 뒤 `java -jar`로 직접 실행한다.

```bash
./gradlew :week09:bootJar
java -jar week09/build/libs/week09-0.0.1-SNAPSHOT.jar
```

`java -jar`는 현재 프로세스가 곧 JVM이므로 stdin이 그대로 전달된다.

#### 함정 2: `spring.ai.mcp.server.stdio=true` 명시 필요

`spring.main.web-application-type: none`만 설정해서는 STDIO transport가 활성화되지 않는다.
서버는 정상 시작되지만(`Registered tools: 5`) JSON-RPC 응답을 쓰지 않는다.

공식 문서:
> To enable the STDIO set `spring.ai.mcp.server.stdio=true`

`spring-ai-starter-mcp-server`는 STDIO가 기본 비활성이다. 반드시 명시해야 한다.

```yaml
spring:
  ai:
    mcp:
      server:
        stdio: true
```

#### 함정 3: 로그가 stdout을 오염시킨다

Spring Boot 로그가 `System.out`으로 출력되고, `StdioServerTransportProvider`도 `System.out`으로 JSON-RPC를 쓴다.
둘이 같은 스트림을 쓰므로 MCP 클라이언트가 로그 줄을 JSON으로 파싱하려다 실패할 수 있다.

공식 예제(spring-ai-examples/weather/starter-stdio-server)의 해결법:
```properties
# NOTE: You must disable the banner and the console logging
# to allow the STDIO transport to work !!!
spring.main.banner-mode=off
logging.pattern.console=
```

`logging.pattern.console=` 빈 값은 콘솔 appender의 출력 패턴을 빈 문자열로 만든다.
실측 결과 빈 줄조차 stdout으로 나가지 않아서 JSON-RPC에 영향을 주지 않는다.
따라서 공식 예제의 방식이 정상 동작한다.

다만 디버깅용으로 로그를 파일에 남기고 싶다면 `logging.file.name`을 함께 설정한다:
```properties
logging.file.name=/tmp/week09-mcp-server.log
```

대안으로 `logback.xml`에서 콘솔 appender를 제거하고 파일 appender만 두는 방법도 있다.
이 경우 `logging.pattern.console=` 설정이 불필요하다.

그러나 `logging.pattern.console=` 빈 값은 콘솔 appender를 끄는 게 아니라 패턴을 빈 문자열로 만들 뿐이다.
빈 줄이 계속 stdout으로 나간다.

**확실한 해결책**: `logback.xml`로 콘솔 appender 자체를 제거하고 파일로만 출력한다.

```xml
<configuration>
    <appender name="FILE" class="ch.qos.logback.core.FileAppender">
        <file>/tmp/week09-mcp-server.log</file>
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    <root level="INFO">
        <appender-ref ref="FILE" />
    </root>
</configuration>
```

#### 함정 4: `opencode.json`의 `environment`가 Gradle → JVM 자식 프로세스로 전달되지 않는다

`environment: { OPENAI_API_KEY: "sk-..." }`를 설정해도 `bootRun`을 쓰면 Gradle이 JVM을 자식 프로세스로 띄우면서
환경변수가 전달되지 않는 경우가 있다. `java -jar` 방식으로 전환하면 같은 프로세스이므로 문제없다.

#### 최종 동작 확인

```bash
# 정상 동작 확인 (stdout에 순수 JSON-RPC만 출력)
echo '{"jsonrpc":"2.0","id":1,"method":"initialize",...}' | \
  OPENAI_API_KEY=sk-... java -jar week09/build/libs/week09-0.0.1-SNAPSHOT.jar

# 응답:
# {"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2024-11-05","serverInfo":{"name":"week09-ai-tools",...}}}
```

#### opencode.json 최종 설정

```json
"faq-bot": {
  "type": "local",
  "command": ["/path/to/week09/mcp-server.sh"],
  "environment": {
    "OPENAI_API_KEY": "sk-..."
  }
}
```

`mcp-server.sh`:
```bash
#!/bin/bash
JAR="$(dirname "$0")/build/libs/week09-0.0.1-SNAPSHOT.jar"
[ ! -f "$JAR" ] && ../gradlew -q :week09:bootJar
exec java -jar "$JAR"
```

### 2. MCP Annotation Scanner가 @Tool을 자동 발견

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

5. **STDIO MCP 서버는 `java -jar`로 실행해야 한다.** `gradlew bootRun`은 Gradle이 JVM을 자식 프로세스로 띄우므로 stdin이 MCP 서버에 전달되지 않는다. Claude Desktop 공식 설정도 항상 `java -jar`를 권장한다.

6. **`spring.ai.mcp.server.stdio=true`는 필수다.** `web-application-type: none`은 웹 서버를 끄는 것이지 STDIO transport를 켜는 것이 아니다. 별도로 명시해야 한다. 이 설정이 없으면 서버가 정상 시작되어도 JSON-RPC 요청을 처리하지 않는다.

7. **`logging.pattern.console=` 빈 값은 정상 동작한다.** 처음에는 빈 줄이 stdout으로 나갈 것이라 예상했지만, 실측 결과 빈 줄조차 출력되지 않았다. 공식 예제의 방식이 맞다. `logback.xml`로 콘솔 appender를 제거하는 것은 대안이지 필수가 아니다.

---

## 다음 주차로 연결

Week 10에서 이어지는 것:
- Week 09의 MCP 서버 + Week 07의 @Tool + Week 08의 멀티 에이전트를 전체 통합
- Docker Compose로 프로덕션 배포
- 전체 스택 (Qdrant + Ollama + Spring Boot + MCP) 통합

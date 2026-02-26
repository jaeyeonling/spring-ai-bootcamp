# 힌트 03: MCP 서버 테스트 및 디버깅

## 테스트 방법

MCP 서버가 올바르게 작동하는지 확인하는 세 가지 방법이 있습니다:

### 1. MCP Inspector (가장 빠른 피드백)

MCP Inspector는 MCP 서버에 연결하여 툴, 리소스, 프롬프트를 대화형으로 테스트할 수 있는
CLI 도구입니다.

```bash
# Inspector 설치 및 실행
npx @modelcontextprotocol/inspector

# STDIO 전송:
npx @modelcontextprotocol/inspector java -jar build/libs/week09.jar

# HTTP/SSE 전송:
npx @modelcontextprotocol/inspector --url http://localhost:8080/mcp
```

Inspector에서 보여주는 것:
- 설명과 파라미터 스키마가 포함된 모든 등록된 툴
- URI가 포함된 모든 리소스
- 파라미터가 포함된 모든 프롬프트
- 각각을 호출하고 결과를 보는 UI

### 2. Claude Desktop 통합

Claude Desktop의 설정 파일에 서버 추가:

**macOS**: `~/Library/Application Support/Claude/claude_desktop_config.json`
**Windows**: `%APPDATA%\Claude\claude_desktop_config.json`

```json
{
  "mcpServers": {
    "week09-ai-tools": {
      "command": "java",
      "args": [
        "-jar",
        "/절대경로/curriculum/week09/build/libs/week09.jar"
      ],
      "env": {
        "OPENAI_API_KEY": "sk-..."
      }
    }
  }
}
```

Claude Desktop을 재시작하세요. 대화에서 사용 가능한 툴로 여러분의 툴이 나타나야 합니다.

### 3. Spring Boot Test로 프로그래밍 방식 테스트

MCP 서비스가 작동하는지 확인하는 통합 테스트 작성:

```java
@SpringBootTest
class McpServerTest {

    @Autowired
    private FaqMcpService faqMcpService;

    @Autowired
    private OrderMcpService orderMcpService;

    @Test
    void searchFaqReturnResults() {
        // @Tool 메서드를 직접 호출 — 일반 Spring 빈 메서드입니다
        List<Map<String, Object>> results = faqMcpService.searchFaq("환불", 3);
        assertThat(results).isNotEmpty();
    }

    @Test
    void lookupOrderReturnsStubData() {
        Map<String, Object> order = orderMcpService.lookupOrder("ORD-12345");
        assertThat(order).containsKey("orderId");
        assertThat(order.get("status")).isEqualTo("배송 중");
    }
}
```

`@Tool` 메서드는 Spring 빈의 일반 Java 메서드이므로 MCP 프로토콜 레이어 없이
테스트에서 직접 호출할 수 있습니다.

## 일반적인 디버깅 문제

### 문제: MCP 클라이언트에서 "Tool not found"

**원인**: `MethodToolCallbackProvider` Bean이 등록되지 않음.

**수정**: `@Configuration` 클래스에서 `MethodToolCallbackProvider` Bean을 등록했는지 확인하세요
(HINT_02 참고).

### 문제: 파라미터가 올바르게 추출되지 않음

**원인**: LLM이 무엇을 전달할지 파악하지 못함.

**수정**: `@ToolParam` 설명을 매우 명확하게 만드세요:
```java
// 나쁨: 모호한 설명
@ToolParam(description = "ID") String id

// 좋음: 예시가 포함된 명확한 설명
@ToolParam(description = "ORD-XXXXX 형식의 주문 ID, 예: ORD-12345") String orderId
```

### 문제: 리소스가 비어있거나 null 반환

**원인**: 리소스 메서드가 null을 반환하거나 반환 전에 예외 발생.

**수정**: 항상 null이 아닌 값을 반환하세요. 빈 목록에는 `List.of()`를 사용하고, null은 안 됩니다.

### 문제: MCP 서버가 시작되지만 툴이 등록되지 않음

**원인**: `spring-ai-starter-mcp-server` 의존성 누락, 또는
어노테이션 스캐너가 비활성화됨.

**수정**: `build.gradle`에서 확인:
```groovy
implementation 'org.springframework.ai:spring-ai-starter-mcp-server'
```

## 전송 모드

MCP 서버는 두 가지 전송을 통해 클라이언트와 통신할 수 있습니다:

### STDIO (CLI 도구의 기본값)

서버가 stdin/stdout으로 JSON-RPC 메시지를 읽고 씁니다.
Claude Desktop과 MCP Inspector가 이것을 사용합니다.

```yaml
spring:
  ai:
    mcp:
      server:
        stdio: true
```

### HTTP/SSE (웹 기반 클라이언트용)

서버가 Server-Sent Events를 통한 스트리밍으로 HTTP 엔드포인트를 노출합니다.
이를 위해 WebMVC 스타터 사용:

```groovy
// 기본 스타터를 WebMVC 스타터로 교체
implementation 'org.springframework.ai:spring-ai-starter-mcp-server-webmvc'
```

## 제출 전 확인 사항

1. `./gradlew :week09:test` 통과
2. MCP Inspector에서 모든 툴, 리소스, 프롬프트 표시
3. 각 툴을 호출할 수 있고 유효한 응답 반환
4. 각 리소스가 데이터 반환
5. 프롬프트 템플릿에 사용 가능한 툴 사용 지침 포함

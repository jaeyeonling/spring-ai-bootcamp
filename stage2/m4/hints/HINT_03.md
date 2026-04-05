# 힌트 03: MCP 서버 설정

@Tool로 만든 기능을 MCP 프로토콜로 외부에 노출합니다.
코드 변경 없이 설정만 추가하면 됩니다.

## 의존성 추가

```gradle
dependencies {
    implementation 'org.springframework.ai:spring-ai-mcp-server-spring-boot-starter'
}
```

## 설정

```yaml
# application.yml
spring:
  ai:
    mcp:
      server:
        enabled: true
        name: cholog-faq-server
        version: 1.0.0
        transport: STDIO   # Claude Desktop, OpenCode 등 STDIO 기반 MCP 클라이언트용
        # transport: SSE   # HTTP 기반 MCP 클라이언트용
```

```java
@SpringBootApplication
@EnableMcpServer  // 이것만 추가 — @Tool 메서드가 자동으로 MCP Tool로 등록됨
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

## STDIO 트랜스포트 실행

```bash
# MCP 서버를 STDIO 모드로 실행
java -jar app.jar --spring.ai.mcp.server.transport=STDIO
```

MCP 클라이언트 설정 (`.mcp.json` 또는 Claude Desktop `claude_desktop_config.json`):

```json
{
  "mcpServers": {
    "cholog-faq": {
      "command": "java",
      "args": ["-jar", "/path/to/app.jar", "--spring.ai.mcp.server.transport=STDIO"]
    }
  }
}
```

## 자동 도구 발견 확인

MCP 클라이언트에서 `tools/list`를 호출하면:

```json
{
  "tools": [
    {
      "name": "getOrderStatus",
      "description": "특정 주문의 현재 상태를 조회합니다...",
      "inputSchema": {
        "type": "object",
        "properties": {
          "orderId": {"type": "string", "description": "조회할 주문번호"}
        }
      }
    },
    {
      "name": "trackDelivery",
      ...
    }
  ]
}
```

@Tool description과 @ToolParam description이 그대로 MCP Tool로 노출됩니다.

## MCP Resources & Prompts (선택)

Tool 외에 MCP에는 두 가지 프리미티브가 더 있습니다:

```java
// Resources: 정적 데이터 노출 (FAQ 문서 목록 등)
@McpResource(uri = "faq://categories", description = "사용 가능한 FAQ 카테고리 목록")
public String getFaqCategories() {
    return String.join(", ", "orders", "shipping", "returns", "payment");
}

// Prompts: 재사용 가능한 프롬프트 템플릿
@McpPrompt(name = "customer-support", description = "고객지원 챗봇 기본 프롬프트")
public String getSystemPrompt() {
    return "당신은 초록 코퍼레이션 고객지원 담당자입니다...";
}
```

## 트러블슈팅

STDIO 모드에서 흔한 문제:

```
문제: MCP 클라이언트가 서버를 찾지 못함
확인: java -jar 경로가 절대 경로인지 확인

문제: Tool이 목록에 안 나옴
확인: @EnableMcpServer가 @SpringBootApplication과 같은 클래스에 있는지 확인

문제: stdout에 로그가 섞임 (STDIO 오염)
해결: 로그를 stderr 또는 파일로 리다이렉트
  logging.file.name: /tmp/cholog-mcp.log
```

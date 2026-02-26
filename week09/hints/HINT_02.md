# 힌트 02: Spring AI 1.1.2로 MCP 서버 만들기

## 올바른 어노테이션

Spring AI 1.1.2는 `org.springframework.ai.tool.annotation`의 `@Tool`과 `@ToolParam`을 사용합니다.

### @Tool — 액션 노출

```java
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

@Service
public class FaqMcpService {

    @Tool(description = "자연어 쿼리로 FAQ 문서를 검색합니다. " +
                        "내용과 메타데이터가 포함된 일치하는 FAQ 항목을 반환합니다. " +
                        "사용자가 FAQ에 있을 수 있는 질문을 할 때 사용하세요.")
    public List<Map<String, Object>> searchFaq(
            @ToolParam(description = "자연어 검색 쿼리") String query,
            @ToolParam(description = "반환할 최대 결과 수 (1-10, 기본 3)") int topK) {

        return vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(topK).build()
        ).stream()
         .map(doc -> Map.of(
                 "content",  (Object) doc.getText(),
                 "metadata", doc.getMetadata()
         ))
         .toList();
    }
}
```

핵심 사항:
- `description`이 AI가 도구를 언제 사용할지 판단하는 기준입니다
- 파라미터 설명은 AI가 올바른 값을 채울 수 있도록 명확하게 작성하세요
- 반환 타입은 자동으로 JSON 직렬화됩니다

### Tool 등록 — MethodToolCallbackProvider Bean

`@Tool` 어노테이션만으로는 MCP 서버에 자동 등록되지 않습니다.
`MethodToolCallbackProvider`를 Bean으로 등록해야 합니다.

```java
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpToolConfig {

    @Bean
    public MethodToolCallbackProvider faqToolProvider(FaqMcpService faqMcpService) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(faqMcpService)
                .build();
    }

    @Bean
    public MethodToolCallbackProvider orderToolProvider(OrderMcpService orderMcpService) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(orderMcpService)
                .build();
    }
}
```

또는 두 서비스를 하나의 Provider에 묶을 수도 있습니다:

```java
@Bean
public MethodToolCallbackProvider allToolsProvider(
        FaqMcpService faqService, OrderMcpService orderService) {
    return MethodToolCallbackProvider.builder()
            .toolObjects(faqService, orderService)
            .build();
}
```

## 완전한 서비스 예시

```java
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class FaqMcpService {

    private final VectorStore vectorStore;

    public FaqMcpService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Tool(description = "자연어 쿼리로 FAQ 문서를 검색합니다. " +
                        "일치하는 FAQ 항목을 반환합니다. " +
                        "사용자가 FAQ에 있을 수 있는 질문을 할 때 사용하세요.")
    public List<Map<String, Object>> searchFaq(
            @ToolParam(description = "자연어 검색 쿼리") String query,
            @ToolParam(description = "반환할 최대 결과 수 (1-10, 기본 3)") int topK) {
        return vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(topK).build()
        ).stream()
         .map(doc -> Map.of(
                 "content",  (Object) doc.getText(),
                 "metadata", doc.getMetadata()
         ))
         .toList();
    }

    // Resource 노출이 필요하면 @Tool로 구현:
    @Tool(description = "사용 가능한 모든 FAQ 문서 카테고리를 나열합니다.")
    public List<Map<String, String>> faqCategories() {
        return List.of(
            Map.of("id", "shipping",  "title", "배송 및 배달"),
            Map.of("id", "returns",   "title", "반품 및 환불"),
            Map.of("id", "payment",   "title", "결제"),
            Map.of("id", "account",   "title", "계정 및 회원")
        );
    }
}
```

## MCP Inspector로 테스트

```bash
# 빌드
./gradlew :week09:bootJar

# MCP Inspector로 연결 (stdio transport)
npx @modelcontextprotocol/inspector java -jar build/libs/week09.jar
```

Inspector에서 등록된 도구 목록, 파라미터 스키마, 반환 타입을 확인할 수 있습니다.

## Claude Desktop에 연결

```json
{
  "mcpServers": {
    "week09-ai-tools": {
      "command": "java",
      "args": ["-jar", "/path/to/week09/build/libs/week09.jar"]
    }
  }
}
```

macOS 설정 파일 위치: `~/Library/Application Support/Claude/claude_desktop_config.json`

## 일반적인 실수

1. **`MethodToolCallbackProvider` Bean 누락** — `@Tool`만 달아도 자동 등록 안 됨.
2. **모호한 도구 설명** — "검색" vs "고객 FAQ를 자연어 쿼리로 검색하여 관련 항목을 반환"
3. **파라미터 설명 누락** — AI가 올바른 값을 채우려면 설명이 필요함

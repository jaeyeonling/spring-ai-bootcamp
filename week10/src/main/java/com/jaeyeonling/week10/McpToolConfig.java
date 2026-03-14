package com.jaeyeonling.week10;

import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP 서버에 @Tool 메서드를 등록하는 설정.
 *
 * Week 10은 HTTP 서버 모드이므로 MCP over HTTP(/mcp/message)로 노출됩니다.
 * STDIO 모드(Week 09)와 달리 stdio: false (기본값).
 */
@Configuration
public class McpToolConfig {

    @Bean
    public MethodToolCallbackProvider mcpToolCallbackProvider(
            FaqSearchTool faqSearchTool,
            OrderTools orderTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(faqSearchTool, orderTools)
                .build();
    }
}

package com.jaeyeonling.stage2.m4;

import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP 서버에 @Tool 메서드를 등록하는 설정.
 *
 * @Tool 어노테이션만으로는 MCP 서버에 자동 등록되지 않는다.
 * MethodToolCallbackProvider를 빈으로 등록해야 MCP 클라이언트가 도구를 발견할 수 있다.
 */
@Configuration
public class McpToolConfig {

    @Bean
    public MethodToolCallbackProvider toolCallbackProvider(
            FaqMcpService faqMcpService,
            OrderMcpService orderMcpService) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(faqMcpService, orderMcpService)
                .build();
    }
}

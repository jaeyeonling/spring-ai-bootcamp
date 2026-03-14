package com.jaeyeonling.week10;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * @Tool 어노테이션이 붙은 모든 빈을 자동 수집해서 ToolCallbackProvider로 등록.
 *
 * ChatClient와 MCP 서버 모두 이 프로바이더를 사용합니다.
 *
 * FaqSearchTool은 ChatClient.Builder에 의존하므로 @Lazy로 순환 의존성 방지.
 * (toolCallbackProvider → FaqSearchTool → ChatClient.Builder → toolCallbackResolver → toolCallbackProvider 순환)
 */
@Configuration
public class ToolConfig {

    @Bean
    public ToolCallbackProvider toolCallbackProvider(
            OrderTools orderTools,
            @Lazy FaqSearchTool faqSearchTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(orderTools, faqSearchTool)
                .build();
    }
}

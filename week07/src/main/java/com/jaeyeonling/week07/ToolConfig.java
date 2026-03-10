package com.jaeyeonling.week07;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Tool 어노테이션이 붙은 모든 빈을 자동 수집해서 ToolCallbackProvider로 등록.
 *
 * 새 @Tool 빈이 추가되면 이 목록에 추가하기만 하면 된다.
 * ChatController는 수정할 필요 없다.
 */
@Configuration
public class ToolConfig {

    @Bean
    public ToolCallbackProvider toolCallbackProvider(
            OrderTools orderTools,
            FaqSearchTool faqSearchTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(orderTools, faqSearchTool)
                .build();
    }
}

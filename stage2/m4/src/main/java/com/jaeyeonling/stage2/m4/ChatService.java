package com.jaeyeonling.stage2.m4;

/**
 * LLM 호출 로직을 캡슐화합니다.
 *
 * Controller는 HTTP만, ReflectionService는 생성-검증-재시도만 담당하도록
 * 실제 LLM 호출과 툴 연결 책임을 이 서비스로 분리합니다.
 */

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private static final String SYSTEM_PROMPT =
            "당신은 도움이 되는 고객 지원 담당자입니다. " +
            "주문, 매출, 회사 정책에 관한 질문에 답하기 위해 사용 가능한 툴을 사용하세요. " +
            "특정 데이터가 필요한 질문에는 항상 툴을 사용하세요 — 답을 꾸며내지 마세요.";

    private final ChatClient chatClient;
    private final ToolCallbackProvider toolCallbackProvider;

    public ChatService(ChatClient.Builder builder, ToolCallbackProvider toolCallbackProvider) {
        this.chatClient = builder.build();
        this.toolCallbackProvider = toolCallbackProvider;
    }

    /**
     * 사용자 질문에 대한 답변을 생성합니다.
     * LLM이 자동으로 올바른 툴을 선택합니다.
     *
     * @param userMessage 사용자의 질문
     * @return LLM이 생성한 답변
     */
    public String chat(String userMessage) {
        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userMessage)
                .toolCallbacks(toolCallbackProvider)
                .call()
                .content();
    }
}

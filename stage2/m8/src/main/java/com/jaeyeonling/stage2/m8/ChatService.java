package com.jaeyeonling.stage2.m8;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

/**
 * 멀티턴 대화를 처리하는 핵심 서비스.
 *
 * ChatMemory를 사용하여 세션별 대화 기록을 유지하고,
 * MessageChatMemoryAdvisor를 통해 이전 맥락을 프롬프트에 주입합니다.
 */
@Service
public class ChatService {

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;

    public ChatService(ChatClient.Builder chatClientBuilder, ChatMemory chatMemory) {
        this.chatMemory = chatMemory;
        this.chatClient = chatClientBuilder
                .defaultAdvisors(buildMemoryAdvisor())
                .build();
    }

    /**
     * 세션 기반 멀티턴 대화를 수행합니다.
     *
     * TODO: 구현을 개선하세요
     * - advisors 파라미터로 CHAT_MEMORY_CONVERSATION_ID_KEY에 sessionId를 전달합니다.
     * - CHAT_MEMORY_RETRIEVE_SIZE_KEY로 최근 N턴을 지정합니다 (슬라이딩 윈도우).
     *
     * 현재는 sessionId를 활용하지 않는 기본 구현입니다.
     *
     * @param sessionId 대화 세션 식별자
     * @param question 사용자 질문
     * @return LLM 답변
     */
    public String chat(String sessionId, String question) {
        // TODO: sessionId를 활용한 세션별 대화 기억 구현으로 개선하세요
        return chatClient.prompt()
                .user(question)
                .call()
                .content();
    }

    /**
     * MessageChatMemoryAdvisor를 생성합니다.
     *
     * TODO: 슬라이딩 윈도우 크기를 조정해보세요.
     *
     * @return 설정된 MessageChatMemoryAdvisor
     */
    private MessageChatMemoryAdvisor buildMemoryAdvisor() {
        // TODO: 슬라이딩 윈도우 크기 등 세부 설정을 개선하세요
        return MessageChatMemoryAdvisor.builder(chatMemory).build();
    }
}

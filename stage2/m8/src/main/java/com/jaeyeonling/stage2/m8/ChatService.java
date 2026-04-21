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
     * TODO: 구현하세요
     * - ChatClient를 사용하여 질문에 답변합니다.
     * - advisors 파라미터로 CHAT_MEMORY_CONVERSATION_ID_KEY에 sessionId를 전달합니다.
     * - CHAT_MEMORY_RETRIEVE_SIZE_KEY로 최근 N턴을 지정합니다 (슬라이딩 윈도우).
     *
     * @param sessionId 대화 세션 식별자
     * @param question 사용자 질문
     * @return LLM 답변
     */
    public String chat(String sessionId, String question) {
        throw new UnsupportedOperationException("구현하세요");
    }

    /**
     * MessageChatMemoryAdvisor를 생성합니다.
     *
     * TODO: 구현하세요
     * - ChatMemory를 사용하여 MessageChatMemoryAdvisor를 구성합니다.
     * - 슬라이딩 윈도우 크기를 설정합니다 (권장: 최근 10턴).
     *
     * @return 설정된 MessageChatMemoryAdvisor
     */
    private MessageChatMemoryAdvisor buildMemoryAdvisor() {
        throw new UnsupportedOperationException("구현하세요");
    }
}

package com.jaeyeonling.week06;

/**
 * 활성 Spring 프로파일에 따라 OpenAI와 Ollama 간에 채팅 요청을 라우팅합니다.
 *
 * 핵심 설계 원칙: 특정 공급자가 아닌 Spring AI의 {@code ChatModel} 추상화에 대해 코딩하세요.
 * "ollama" 프로파일이 활성화되면 Spring Boot가 {@code OllamaChatModel}을 자동 설정합니다.
 * 그렇지 않으면 {@code OpenAiChatModel}이 사용됩니다.
 *
 * 공급자 전환을 위한 if/else 없음 — Spring 프로파일만 사용합니다.
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

@Service
public class ModelRoutingService {

    private static final Logger log = LoggerFactory.getLogger(ModelRoutingService.class);

    private final ChatClient chatClient;
    private final ChatModel chatModel;

    public ModelRoutingService(ChatModel chatModel) {
        this.chatModel = chatModel;
        this.chatClient = ChatClient.create(chatModel);
        log.info("ModelRoutingService가 ChatModel로 초기화됨: {}", chatModel.getClass().getSimpleName());
    }

    /**
     * 단순한 사용자 메시지를 전송하고 응답을 받습니다.
     *
     * @param userMessage 사용자의 질문
     * @return 모델의 응답 텍스트
     */
    public String chat(String userMessage) {
        return chatClient.prompt()
                .system("당신은 도움이 되는 고객 지원 담당자입니다.")
                .user(userMessage)
                .call()
                .content();
    }

    /**
     * 커스텀 시스템 프롬프트로 메시지를 전송하고 전체 ChatResponse를 받습니다
     * (토큰 사용량 메타데이터 포함).
     *
     * @param systemPrompt 컨텍스트를 설정하는 시스템 프롬프트
     * @param userMessage  사용자의 질문
     * @return 메타데이터가 포함된 전체 ChatResponse
     */
    public ChatResponse chatWithMetadata(String systemPrompt, String userMessage) {
        return chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .call()
                .chatResponse();
    }

    /**
     * 활성 모델 공급자 이름을 반환합니다 (로깅 및 비용 추적용).
     *
     * @return 공급자를 식별하는 문자열 (예: "OpenAiChatModel" 또는 "OllamaChatModel")
     */
    public String getActiveProvider() {
        return chatModel.getClass().getSimpleName();
    }
}

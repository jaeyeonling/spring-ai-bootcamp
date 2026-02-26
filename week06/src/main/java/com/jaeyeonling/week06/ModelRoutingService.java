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

    public ModelRoutingService(ChatModel chatModel) {
        // TODO: 주입된 ChatModel로 ChatClient를 생성하세요.
        //  ChatModel은 활성 Spring 프로파일에 따라 OpenAiChatModel 또는
        //  OllamaChatModel이 됩니다. 코드에서 어느 것인지 알 필요가 없습니다.
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
        // TODO: chatClient를 사용해 사용자 메시지를 전송하고 내용을 반환하세요.
        //  공급자 간 일관된 동작을 위해 시스템 프롬프트 추가를 고려하세요.
        //  예시:
        //    return chatClient.prompt()
        //        .system("당신은 도움이 되는 고객 지원 담당자입니다.")
        //        .user(userMessage)
        //        .call()
        //        .content();

        throw new UnsupportedOperationException("구현하세요 — mission/MISSION.md 참고");
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
        // TODO: chatClient를 사용해 메시지를 전송하고 전체 ChatResponse를 반환하세요.
        //  ChatResponse에는 토큰 사용량, 모델 정보, 기타 메타데이터가 포함됩니다.
        //  비용 추적과 모니터링에 유용합니다.
        //  예시:
        //    return chatClient.prompt()
        //        .system(systemPrompt)
        //        .user(userMessage)
        //        .call()
        //        .chatResponse();

        throw new UnsupportedOperationException("구현하세요 — mission/MISSION.md 참고");
    }

    /**
     * 활성 모델 공급자 이름을 반환합니다 (로깅 및 비용 추적용).
     *
     * @return 공급자를 식별하는 문자열 (예: "OpenAiChatModel" 또는 "OllamaChatModel")
     */
    public String getActiveProvider() {
        // TODO: 기본 ChatModel의 클래스 이름을 반환하세요.
        //  어떤 공급자가 요청을 처리했는지 로깅하는 데 유용하며,
        //  이는 비용 분석에 기여합니다.

        throw new UnsupportedOperationException("구현하세요 — mission/MISSION.md 참고");
    }
}

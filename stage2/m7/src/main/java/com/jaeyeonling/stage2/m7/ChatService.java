package com.jaeyeonling.stage2.m7;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * 인프라 검증용 최소 ChatService.
 *
 * Docker 환경에서 OpenAI 연결이 정상인지 확인하기 위한 용도입니다.
 * 복잡한 RAG 파이프라인은 이 모듈의 범위가 아닙니다.
 */
@Service
public class ChatService {

    private final ChatClient chatClient;

    public ChatService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public String ask(String question) {
        return chatClient.prompt()
                .user(question)
                .call()
                .content();
    }
}

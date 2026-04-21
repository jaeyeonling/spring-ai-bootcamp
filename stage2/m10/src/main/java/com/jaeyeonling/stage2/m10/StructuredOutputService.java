package com.jaeyeonling.stage2.m10;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * 구조화 출력(Structured Output)을 처리하는 서비스.
 *
 * entity() API를 사용하여 LLM 응답을 ChatAnswer 레코드로 직접 변환하고,
 * 스키마 위반 시 자동으로 재시도합니다.
 */
@Service
public class StructuredOutputService {

    private static final Logger log = LoggerFactory.getLogger(StructuredOutputService.class);
    private static final int MAX_RETRIES = 3;

    private final ChatClient chatClient;

    public StructuredOutputService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * 구조화된 응답을 요청합니다.
     *
     * TODO: 구현하세요
     * - ChatClient의 .call().entity(ChatAnswer.class)를 사용합니다.
     * - 스키마 위반(파싱 실패) 시 최대 MAX_RETRIES까지 재시도합니다.
     * - 각 재시도 시 로그를 남깁니다.
     * - 모든 재시도 실패 시 예외를 throw합니다.
     *
     * @param question 사용자 질문
     * @return 구조화된 ChatAnswer 응답
     */
    public ChatAnswer ask(String question) {
        throw new UnsupportedOperationException("구현하세요");
    }
}

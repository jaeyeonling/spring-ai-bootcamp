package com.jaeyeonling.stage2.m8;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ChatMemory 빈 설정.
 *
 * 개발/테스트 환경에서는 InMemoryChatMemoryRepository를 사용하고,
 * 프로덕션 환경에서는 Redis 등 외부 저장소를 사용할 수 있습니다.
 */
@Configuration
public class ChatMemoryConfig {

    /**
     * ChatMemory 빈을 생성합니다.
     *
     * TODO: 프로덕션 환경에서는 외부 ChatMemoryRepository로 교체하세요.
     *
     * @return ChatMemory 인스턴스
     */
    @Bean
    public ChatMemory chatMemory() {
        // TODO: 프로덕션 환경에서는 외부 저장소 기반 ChatMemoryRepository로 교체하세요
        return MessageWindowChatMemory.builder().build();
    }
}

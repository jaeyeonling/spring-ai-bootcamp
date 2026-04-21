package com.jaeyeonling.stage2.m8;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ChatMemory 빈 설정.
 *
 * 개발/테스트 환경에서는 InMemoryChatMemory를 사용하고,
 * 프로덕션 환경에서는 Redis 등 외부 저장소를 사용할 수 있습니다.
 */
@Configuration
public class ChatMemoryConfig {

    /**
     * ChatMemory 빈을 생성합니다.
     *
     * TODO: 구현하세요
     * - InMemoryChatMemory 인스턴스를 반환합니다.
     * - 프로덕션 환경을 고려하여 @Profile을 활용할 수도 있습니다.
     *
     * @return ChatMemory 인스턴스
     */
    @Bean
    public ChatMemory chatMemory() {
        throw new UnsupportedOperationException("구현하세요");
    }
}

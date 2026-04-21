package com.jaeyeonling.stage2.m8;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Component;

/**
 * 대화 세션의 생명주기를 관리합니다.
 *
 * 30분 비활성 시 세션을 만료하고, ChatMemory에서 해당 세션 기록을 제거합니다.
 */
@Component
public class SessionManager {

    private static final Duration SESSION_TIMEOUT = Duration.ofMinutes(30);

    private final ChatMemory chatMemory;
    private final Map<String, Instant> lastAccessTimes = new ConcurrentHashMap<>();

    public SessionManager(ChatMemory chatMemory) {
        this.chatMemory = chatMemory;
    }

    /**
     * 세션 접근 시간을 갱신합니다.
     *
     * TODO: 구현하세요
     * - 현재 시간을 lastAccessTimes에 기록합니다.
     *
     * @param sessionId 세션 식별자
     */
    public void touch(String sessionId) {
        throw new UnsupportedOperationException("구현하세요");
    }

    /**
     * 세션이 만료되었는지 확인합니다.
     *
     * TODO: 구현하세요
     * - 마지막 접근 시간으로부터 SESSION_TIMEOUT이 경과했는지 판단합니다.
     * - 기록이 없는 세션은 만료된 것으로 간주합니다.
     *
     * @param sessionId 세션 식별자
     * @return 만료 여부
     */
    public boolean isExpired(String sessionId) {
        throw new UnsupportedOperationException("구현하세요");
    }

    /**
     * 만료된 세션들을 정리합니다.
     *
     * TODO: 구현하세요
     * - lastAccessTimes에서 만료된 세션을 찾습니다.
     * - ChatMemory에서 해당 세션의 대화 기록을 삭제합니다.
     * - lastAccessTimes에서도 제거합니다.
     */
    public void cleanupExpiredSessions() {
        throw new UnsupportedOperationException("구현하세요");
    }
}

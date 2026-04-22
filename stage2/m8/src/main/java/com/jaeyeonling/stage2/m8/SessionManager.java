package com.jaeyeonling.stage2.m8;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Component;

/**
 * 대화 세션의 생명주기를 관리합니다.
 *
 * 30분 비활성 시 세션을 만료하고, ChatMemory에서 해당 세션 기록을 제거합니다.
 */
@Component
public class SessionManager {

    private static final Logger log = LoggerFactory.getLogger(SessionManager.class);
    private static final Duration SESSION_TIMEOUT = Duration.ofMinutes(30);

    private final ChatMemory chatMemory;
    private final Map<String, Instant> lastAccessTimes = new ConcurrentHashMap<>();

    public SessionManager(ChatMemory chatMemory) {
        this.chatMemory = chatMemory;
    }

    /**
     * 세션 접근 시간을 갱신합니다.
     *
     * @param sessionId 세션 식별자
     */
    public void touch(String sessionId) {
        lastAccessTimes.put(sessionId, Instant.now());
    }

    /**
     * 세션이 만료되었는지 확인합니다.
     *
     * @param sessionId 세션 식별자
     * @return 만료 여부
     */
    public boolean isExpired(String sessionId) {
        Instant lastAccess = lastAccessTimes.get(sessionId);
        if (lastAccess == null) {
            return true;
        }
        return Duration.between(lastAccess, Instant.now()).compareTo(SESSION_TIMEOUT) > 0;
    }

    /**
     * 만료된 세션들을 정리합니다.
     *
     * TODO: 주기적 실행을 위해 @Scheduled 어노테이션 추가를 고려하세요.
     */
    public void cleanupExpiredSessions() {
        lastAccessTimes.entrySet().removeIf(entry -> {
            boolean expired = Duration.between(entry.getValue(), Instant.now())
                    .compareTo(SESSION_TIMEOUT) > 0;
            if (expired) {
                chatMemory.clear(entry.getKey());
                log.info("[Session] 만료된 세션 정리: {}", entry.getKey());
            }
            return expired;
        });
    }
}

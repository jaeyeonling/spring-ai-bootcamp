package com.jaeyeonling.stage2.m11;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

/**
 * 상담 로그에서 반복되는 질문 패턴을 감지합니다.
 *
 * 7일 이내에 3회 이상 등장한 동일 의도(intent)의 질문을 그룹화합니다.
 */
@Service
public class PatternDetector {

    private static final int MIN_OCCURRENCES = 3;
    private static final int WINDOW_DAYS = 7;

    /**
     * 최근 상담 로그에서 반복 패턴을 감지합니다.
     *
     * TODO: 구현하세요
     * - recentLogs에서 동일 intent를 가진 질문을 그룹화합니다.
     * - WINDOW_DAYS(7일) 이내에 MIN_OCCURRENCES(3회) 이상 등장한 그룹만 필터링합니다.
     * - 각 그룹을 RepeatedPattern으로 변환하여 반환합니다.
     *
     * @param recentLogs 분석 대상 상담 로그 목록
     * @return 반복 패턴 목록
     */
    public List<RepeatedPattern> detect(List<ChatLog> recentLogs) {
        throw new UnsupportedOperationException("구현하세요");
    }

    /**
     * 상담 로그 레코드.
     */
    public record ChatLog(
        String id,
        String question,
        String answer,
        String primaryIntent,
        LocalDateTime createdAt
    ) {}

    /**
     * 감지된 반복 패턴.
     */
    public record RepeatedPattern(
        String intent,
        List<ChatLog> cases,
        int occurrenceCount
    ) {}
}

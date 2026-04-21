package com.jaeyeonling.stage2.m6;

import java.util.List;

/**
 * 에이전트의 처리 결과.
 *
 * @param answer     에이전트의 응답 텍스트
 * @param sources    참조한 문서/소스 목록
 * @param confidence 응답의 확신도 (0.0 ~ 1.0)
 */
public record AgentResponse(String answer, List<String> sources, double confidence) {

    public AgentResponse(String answer, double confidence) {
        this(answer, List.of(), confidence);
    }
}

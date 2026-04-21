package com.jaeyeonling.stage2.m6;

import java.util.Map;

/**
 * 에이전트에 전달되는 요청.
 *
 * @param question 사용자 질문
 * @param context  오케스트레이터 또는 이전 에이전트에서 전달하는 추가 컨텍스트
 */
public record AgentRequest(String question, Map<String, Object> context) {

    public AgentRequest(String question) {
        this(question, Map.of());
    }
}

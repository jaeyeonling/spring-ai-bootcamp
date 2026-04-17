package com.jaeyeonling.stage2.m7;

/**
 * FAQ 멀티 에이전트 워크플로우의 모든 에이전트를 위한 공통 인터페이스.
 *
 * Week 08의 Agent 인터페이스를 FAQ 챗봇 도메인으로 적용.
 * 각 에이전트는 하나의 전문 영역(주문/정책/추천)을 담당합니다.
 */
public interface FaqAgent {

    /**
     * 에이전트의 작업을 실행합니다.
     *
     * @param question 사용자 질문
     * @param context  오케스트레이터 또는 이전 에이전트의 추가 컨텍스트
     * @return 에이전트의 답변
     */
    String execute(String question, String context);

    /**
     * @return 이 에이전트의 이름 (로그에서 사용)
     */
    default String name() {
        return getClass().getSimpleName();
    }
}

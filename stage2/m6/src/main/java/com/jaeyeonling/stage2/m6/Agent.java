package com.jaeyeonling.stage2.m6;

/**
 * 멀티 에이전트 워크플로우의 모든 에이전트를 위한 공통 인터페이스.
 *
 * 각 에이전트는 하나의 작업을 잘 수행하는 전문가입니다.
 * 에이전트는 원본 입력과 이전 에이전트의 컨텍스트를 받습니다
 * (예: 체인 워크플로우에서 에이전트 A의 출력이 에이전트 B의 컨텍스트가 됩니다).
 */
public interface Agent {

    /**
     * 에이전트의 작업을 실행합니다.
     *
     * @param input   주요 입력 (예: PR diff)
     * @param context 오케스트레이터 또는 체인의 이전 에이전트로부터의 추가 컨텍스트
     * @return 에이전트의 출력 문자열
     */
    String execute(String input, String context);

    /**
     * @return 이 에이전트의 사람이 읽을 수 있는 이름 (로그와 리포트에서 사용)
     */
    default String name() {
        return getClass().getSimpleName();
    }
}

package com.jaeyeonling.stage2.m6;

import org.springframework.stereotype.Component;

/**
 * Layer 3: 상담 로그 선례 탐색 에이전트.
 *
 * 과거 상담 기록에서 유사한 사례를 찾아 선례를 제공합니다.
 * 정책에 명시되지 않은 예외 처리 사례(VIP 예외 등)를 발견합니다.
 *
 * 구현 시 고려사항:
 * - VectorStore에서 상담 로그 검색
 * - 유사 사례의 처리 결과(승인/거부)와 사유 추출
 * - 선례의 일관성 평가 (같은 유형에서 다른 결정이 있었는지)
 * - confidence는 유사 선례 수와 일관성 기반으로 설정
 */
@Component
public class ChatHistoryAgent implements Agent {

    @Override
    public String execute(String input, String context) {
        // TODO: 구현하세요
        // 1. input(질문)을 VectorStore에서 상담 로그 검색
        // 2. 유사 사례의 처리 결과와 사유 추출
        // 3. 선례 요약 텍스트 반환
        throw new UnsupportedOperationException("구현하세요");
    }

    /**
     * AgentRequest/AgentResponse 기반의 처리 메서드.
     *
     * @param request 에이전트 요청
     * @return 유사 선례 기반 응답
     */
    public AgentResponse process(AgentRequest request) {
        // TODO: 구현하세요
        // 1. request.question()을 VectorStore에서 상담 로그 검색
        // 2. 유사 사례 추출 및 처리 결과 분석
        // 3. AgentResponse(선례 요약, 참조 로그, 확신도) 반환
        throw new UnsupportedOperationException("구현하세요");
    }

    @Override
    public String name() {
        return "ChatHistoryAgent";
    }

    public String getDescription() {
        return "과거 상담 로그에서 유사한 선례를 찾아 처리 결과를 제공합니다.";
    }
}

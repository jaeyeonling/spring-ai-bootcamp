package com.jaeyeonling.stage2.m6;

import org.springframework.stereotype.Component;

/**
 * Layer 2: 정책 문서 전담 에이전트.
 *
 * 정책 문서에서 질문에 대한 답변을 검색합니다.
 * 정책 버전(v1, v2, v3)을 판단하여 최신 정책 기준으로 답변합니다.
 *
 * 구현 시 고려사항:
 * - VectorStore에서 정책 문서 검색
 * - 정책 버전 메타데이터 확인 (최신 버전 우선)
 * - 버전 간 충돌 시 최신 정책을 기준으로 답변
 * - confidence는 정책 적용 가능성 기반으로 설정
 */
@Component
public class PolicyAgent implements Agent {

    @Override
    public String execute(String input, String context) {
        // TODO: 구현하세요
        // 1. input(질문)을 VectorStore에서 정책 문서 검색
        // 2. 정책 버전 메타데이터 확인, 최신 버전 우선 적용
        // 3. 검색된 문서를 컨텍스트로 LLM 호출
        // 4. 답변 텍스트 반환
        throw new UnsupportedOperationException("구현하세요");
    }

    /**
     * AgentRequest/AgentResponse 기반의 처리 메서드.
     *
     * @param request 에이전트 요청
     * @return 정책 문서 기반 응답 (버전 정보 포함)
     */
    public AgentResponse process(AgentRequest request) {
        // TODO: 구현하세요
        // 1. request.question()을 VectorStore에서 정책 문서 검색
        // 2. 버전별 정책 필터링 (최신 우선)
        // 3. AgentResponse(답변, 참조 정책 문서, 확신도) 반환
        throw new UnsupportedOperationException("구현하세요");
    }

    @Override
    public String name() {
        return "PolicyAgent";
    }

    public String getDescription() {
        return "정책 문서에서 버전을 고려하여 규정 기반 답변을 제공합니다.";
    }
}

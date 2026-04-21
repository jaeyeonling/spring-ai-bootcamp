package com.jaeyeonling.stage2.m6;

import org.springframework.stereotype.Component;

/**
 * Layer 1: FAQ 전담 에이전트.
 *
 * FAQ 데이터에서 질문에 대한 답변을 검색합니다.
 * 단순한 사실적 질문(배송 기간, 반품 정책 기본 안내 등)을 처리합니다.
 *
 * 구현 시 고려사항:
 * - VectorStore에서 FAQ 문서 검색
 * - 검색 결과를 바탕으로 LLM이 답변 생성
 * - confidence는 검색 유사도 기반으로 설정
 */
@Component
public class FaqAgent implements Agent {

    @Override
    public String execute(String input, String context) {
        // TODO: 구현하세요
        // 1. input(질문)을 VectorStore에서 FAQ 문서 검색
        // 2. 검색된 문서를 컨텍스트로 LLM 호출
        // 3. 답변 텍스트 반환
        throw new UnsupportedOperationException("구현하세요");
    }

    /**
     * AgentRequest/AgentResponse 기반의 처리 메서드.
     *
     * @param request 에이전트 요청
     * @return FAQ 검색 기반 응답
     */
    public AgentResponse process(AgentRequest request) {
        // TODO: 구현하세요
        // 1. request.question()을 VectorStore에서 FAQ 문서 검색
        // 2. 검색된 문서를 컨텍스트로 LLM 호출
        // 3. AgentResponse(답변, 소스 목록, 확신도) 반환
        throw new UnsupportedOperationException("구현하세요");
    }

    @Override
    public String name() {
        return "FaqAgent";
    }

    public String getDescription() {
        return "FAQ 데이터에서 단순 사실적 질문에 대한 답변을 검색합니다.";
    }
}

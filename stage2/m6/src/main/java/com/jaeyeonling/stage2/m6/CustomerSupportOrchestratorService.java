package com.jaeyeonling.stage2.m6;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 고객 지원 오케스트레이터: 질문을 분석하고 적절한 에이전트를 조합하여 최종 답변을 생성합니다.
 *
 * Orchestrator-Worker 패턴:
 * 1. 질문 분석 — 어떤 에이전트가 필요한지 판단
 * 2. 에이전트 실행 — 필요한 에이전트를 병렬로 호출
 * 3. 결과 합성 — 에이전트 응답을 종합하여 최종 답변 생성
 *
 * 에이전트 간 모순 처리:
 * - 최신 정책 문서 우선
 * - 선례가 있으면 예외 가능성 언급
 * - 모순을 사용자에게 투명하게 노출
 */
@Service
public class CustomerSupportOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(CustomerSupportOrchestratorService.class);

    private final FaqAgent faqAgent;
    private final PolicyAgent policyAgent;
    private final ChatHistoryAgent chatHistoryAgent;

    public CustomerSupportOrchestratorService(FaqAgent faqAgent,
                                              PolicyAgent policyAgent,
                                              ChatHistoryAgent chatHistoryAgent) {
        this.faqAgent = faqAgent;
        this.policyAgent = policyAgent;
        this.chatHistoryAgent = chatHistoryAgent;
    }

    /**
     * 질문을 분석하여 적절한 에이전트를 선택합니다.
     *
     * @param question 사용자 질문
     * @return 호출할 에이전트 목록
     */
    public List<Agent> routeToAgents(String question) {
        // TODO: 구현하세요
        // 1. 질문 복잡도/유형 분석 (단순 FAQ vs 정책 필요 vs 교차 소스)
        // 2. 단순 질문: FaqAgent만
        // 3. 정책 관련 질문: FaqAgent + PolicyAgent
        // 4. 교차 소스 질문 (VIP, 예외 등): 3개 에이전트 모두
        throw new UnsupportedOperationException("구현하세요");
    }

    /**
     * 질문에 대한 최종 답변을 생성합니다.
     *
     * @param question 사용자 질문
     * @return 종합된 최종 답변
     */
    public String answer(String question) {
        // TODO: 구현하세요
        // 1. routeToAgents()로 필요한 에이전트 선택
        // 2. 선택된 에이전트를 병렬로 실행
        // 3. 에이전트 응답을 합성하여 최종 답변 생성
        // 4. 모순이 있으면 최신 정책 우선 + 예외 가능성 언급
        throw new UnsupportedOperationException("구현하세요");
    }

    /**
     * 교차 소스 질문에 대해 모든 에이전트를 실행하고 결과를 합성합니다.
     *
     * @param question 교차 소스 질문
     * @return 합성된 AgentResponse
     */
    public AgentResponse answerCrossSource(String question) {
        // TODO: 구현하세요
        // 1. 3개 에이전트 병렬 실행
        // 2. 모순 감지 및 해결
        // 3. 합성된 응답 반환
        throw new UnsupportedOperationException("구현하세요");
    }

    /**
     * 등록된 에이전트 목록을 반환합니다.
     *
     * @return 사용 가능한 에이전트 맵 (이름 → 에이전트)
     */
    public Map<String, Agent> getAvailableAgents() {
        return Map.of(
                faqAgent.name(), faqAgent,
                policyAgent.name(), policyAgent,
                chatHistoryAgent.name(), chatHistoryAgent
        );
    }
}

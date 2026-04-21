package com.jaeyeonling.stage2.m6;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 멀티 에이전트 시스템 테스트.
 *
 * 오케스트레이터의 라우팅 로직과 에이전트 조합 패턴을 검증합니다.
 * 학습자가 구현을 완료하면 이 테스트들이 통과해야 합니다.
 */
class MultiAgentTest {

    @Nested
    @DisplayName("에이전트 인터페이스 구현")
    class AgentInterfaceTest {

        @Test
        @DisplayName("FaqAgent가 Agent 인터페이스를 구현하고 고유 이름을 가진다")
        void faqAgentImplementsInterface() {
            var agent = new FaqAgent();
            assertThat(agent).isInstanceOf(Agent.class);
            assertThat(agent.name()).isEqualTo("FaqAgent");
            assertThat(agent.getDescription()).isNotBlank();
        }

        @Test
        @DisplayName("PolicyAgent가 Agent 인터페이스를 구현하고 고유 이름을 가진다")
        void policyAgentImplementsInterface() {
            var agent = new PolicyAgent();
            assertThat(agent).isInstanceOf(Agent.class);
            assertThat(agent.name()).isEqualTo("PolicyAgent");
            assertThat(agent.getDescription()).isNotBlank();
        }

        @Test
        @DisplayName("ChatHistoryAgent가 Agent 인터페이스를 구현하고 고유 이름을 가진다")
        void chatHistoryAgentImplementsInterface() {
            var agent = new ChatHistoryAgent();
            assertThat(agent).isInstanceOf(Agent.class);
            assertThat(agent.name()).isEqualTo("ChatHistoryAgent");
            assertThat(agent.getDescription()).isNotBlank();
        }

        @Test
        @DisplayName("모든 에이전트가 서로 다른 이름을 가진다")
        void agentsHaveDistinctNames() {
            var faq = new FaqAgent();
            var policy = new PolicyAgent();
            var chatHistory = new ChatHistoryAgent();

            assertThat(faq.name()).isNotEqualTo(policy.name());
            assertThat(policy.name()).isNotEqualTo(chatHistory.name());
            assertThat(faq.name()).isNotEqualTo(chatHistory.name());
        }
    }

    @Nested
    @DisplayName("오케스트레이터 라우팅")
    class OrchestratorRoutingTest {

        private final FaqAgent faqAgent = new FaqAgent();
        private final PolicyAgent policyAgent = new PolicyAgent();
        private final ChatHistoryAgent chatHistoryAgent = new ChatHistoryAgent();
        private final CustomerSupportOrchestratorService orchestrator =
                new CustomerSupportOrchestratorService(faqAgent, policyAgent, chatHistoryAgent);

        @Test
        @DisplayName("단순 FAQ 질문은 FaqAgent로 라우팅된다")
        void simpleQuestionRoutesToFaqAgent() {
            // Arrange
            String question = "배송 기간이 얼마나 되나요?";

            // Act
            List<Agent> agents = orchestrator.routeToAgents(question);

            // Assert
            assertThat(agents).contains(faqAgent);
            assertThat(agents).doesNotContain(chatHistoryAgent);
        }

        @Test
        @DisplayName("정책 관련 질문은 PolicyAgent를 포함한다")
        void policyQuestionIncludesPolicyAgent() {
            // Arrange
            String question = "냉장 식품 반품 정책이 어떻게 되나요?";

            // Act
            List<Agent> agents = orchestrator.routeToAgents(question);

            // Assert
            assertThat(agents).contains(policyAgent);
        }

        @Test
        @DisplayName("교차 소스 질문은 모든 에이전트를 사용한다")
        void crossSourceQuestionUsesAllAgents() {
            // Arrange — VIP + 마켓플레이스 + 냉장식품: 교차 소스 질문
            String question = "VIP 회원인데 마켓플레이스에서 산 냉장 식품 반품할 수 있나요?";

            // Act
            List<Agent> agents = orchestrator.routeToAgents(question);

            // Assert
            assertThat(agents).hasSize(3);
            assertThat(agents).contains(faqAgent, policyAgent, chatHistoryAgent);
        }

        @Test
        @DisplayName("오케스트레이터가 등록된 에이전트 목록을 반환한다")
        void returnsAvailableAgents() {
            // Act
            Map<String, Agent> agents = orchestrator.getAvailableAgents();

            // Assert
            assertThat(agents).hasSize(3);
            assertThat(agents).containsKeys("FaqAgent", "PolicyAgent", "ChatHistoryAgent");
        }
    }

    @Nested
    @DisplayName("에이전트 실행")
    class AgentExecutionTest {

        @Test
        @DisplayName("FaqAgent.process가 구현 전에는 UnsupportedOperationException을 던진다")
        void faqAgentProcessThrowsBeforeImplementation() {
            var agent = new FaqAgent();
            var request = new AgentRequest("배송 기간이 얼마나 되나요?");

            assertThatThrownBy(() -> agent.process(request))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessage("구현하세요");
        }

        @Test
        @DisplayName("PolicyAgent.process가 구현 전에는 UnsupportedOperationException을 던진다")
        void policyAgentProcessThrowsBeforeImplementation() {
            var agent = new PolicyAgent();
            var request = new AgentRequest("반품 정책이 어떻게 되나요?");

            assertThatThrownBy(() -> agent.process(request))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessage("구현하세요");
        }

        @Test
        @DisplayName("ChatHistoryAgent.process가 구현 전에는 UnsupportedOperationException을 던진다")
        void chatHistoryAgentProcessThrowsBeforeImplementation() {
            var agent = new ChatHistoryAgent();
            var request = new AgentRequest("VIP 예외 처리 선례가 있나요?");

            assertThatThrownBy(() -> agent.process(request))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessage("구현하세요");
        }

        @Test
        @DisplayName("오케스트레이터의 answer가 구현 전에는 UnsupportedOperationException을 던진다")
        void orchestratorAnswerThrowsBeforeImplementation() {
            var orchestrator = new CustomerSupportOrchestratorService(
                    new FaqAgent(), new PolicyAgent(), new ChatHistoryAgent());

            assertThatThrownBy(() -> orchestrator.answer("배송 기간이 얼마나 되나요?"))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessage("구현하세요");
        }

        @Test
        @DisplayName("오케스트레이터의 answerCrossSource가 구현 전에는 UnsupportedOperationException을 던진다")
        void orchestratorCrossSourceThrowsBeforeImplementation() {
            var orchestrator = new CustomerSupportOrchestratorService(
                    new FaqAgent(), new PolicyAgent(), new ChatHistoryAgent());

            assertThatThrownBy(() -> orchestrator.answerCrossSource(
                    "VIP 회원인데 마켓플레이스에서 산 냉장 식품 반품할 수 있나요?"))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessage("구현하세요");
        }
    }

    @Nested
    @DisplayName("AgentRequest / AgentResponse")
    class RecordTest {

        @Test
        @DisplayName("AgentRequest에 질문만으로 생성할 수 있다")
        void agentRequestWithQuestionOnly() {
            var request = new AgentRequest("질문입니다");

            assertThat(request.question()).isEqualTo("질문입니다");
            assertThat(request.context()).isEmpty();
        }

        @Test
        @DisplayName("AgentRequest에 컨텍스트를 포함하여 생성할 수 있다")
        void agentRequestWithContext() {
            var request = new AgentRequest("질문", Map.of("key", "value"));

            assertThat(request.question()).isEqualTo("질문");
            assertThat(request.context()).containsEntry("key", "value");
        }

        @Test
        @DisplayName("AgentResponse에 답변과 확신도로 생성할 수 있다")
        void agentResponseWithAnswerAndConfidence() {
            var response = new AgentResponse("답변입니다", 0.85);

            assertThat(response.answer()).isEqualTo("답변입니다");
            assertThat(response.confidence()).isEqualTo(0.85);
            assertThat(response.sources()).isEmpty();
        }

        @Test
        @DisplayName("AgentResponse에 소스를 포함하여 생성할 수 있다")
        void agentResponseWithSources() {
            var response = new AgentResponse("답변", List.of("FAQ#12", "Policy-v3#5"), 0.9);

            assertThat(response.sources()).containsExactly("FAQ#12", "Policy-v3#5");
        }
    }
}

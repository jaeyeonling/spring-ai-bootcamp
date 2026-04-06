package com.jaeyeonling.week05;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 관찰 가능성 설정 테스트.
 *
 * Actuator, Micrometer 메트릭, 트레이싱이 올바르게 설정되었는지 검증합니다.
 * 실행: ./gradlew :week05:test
 *
 * 참고: 일부 테스트는 실행 중인 Qdrant 인스턴스와 OPENAI_API_KEY가 필요합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ObservabilityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MetricsService metricsService;

    @Autowired
    private EvaluationService evaluationService;

    @Test
    @DisplayName("Actuator health 엔드포인트가 200을 반환한다")
    void actuatorHealthReturns200() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").exists());
    }

    @Test
    @DisplayName("Actuator health에 커스텀 헬스 인디케이터가 포함된다")
    void actuatorHealthIncludesCustomIndicators() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.components.openAi").exists())
            .andExpect(jsonPath("$.components.vectorStore").exists());
    }

    @Test
    @DisplayName("Actuator metrics 엔드포인트가 사용 가능한 메트릭 목록을 반환한다")
    void actuatorMetricsListsMetrics() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.names").isArray());
    }

    @Test
    @DisplayName("커스텀 RAG 메트릭이 Micrometer에 등록된다")
    void customRagMetricsAreRegistered() throws Exception {
        // 커스텀 메트릭이 레지스트리에 존재하는지 검증
        mockMvc.perform(get("/actuator/metrics/rag.search.latency"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("rag.search.latency"));

        mockMvc.perform(get("/actuator/metrics/rag.llm.token.usage"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("rag.llm.token.usage"));

        mockMvc.perform(get("/actuator/metrics/rag.hallucination.detected"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("rag.hallucination.detected"));

        mockMvc.perform(get("/actuator/metrics/rag.answer.quality.score"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("rag.answer.quality.score"));
    }

    @Test
    @DisplayName("MetricsService가 주입 가능하고 작동한다")
    void metricsServiceIsInjectable() {
        // 기본 스모크 테스트 — 서비스가 올바르게 연결되었는지 확인
        assertThat(metricsService).isNotNull();
    }

    @Test
    @DisplayName("EvaluationService가 주입 가능하고 작동한다")
    void evaluationServiceIsInjectable() {
        assertThat(evaluationService).isNotNull();
    }

    // -----------------------------------------------------------------------
    // 통합 테스트 — 실제 API 키와 실행 중인 서비스가 필요합니다
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("[통합] 배치 평가가 실행되고 결과를 생성한다")
    void batchEvaluationProducesResults() {
        List<EvaluationService.TestCase> testCases = List.of(
            new EvaluationService.TestCase(
                "반품 정책이 어떻게 되나요?",
                "30일 이내에 반품 가능합니다"
            ),
            new EvaluationService.TestCase(
                "어떤 결제 방법을 지원하나요?",
                "신용카드, PayPal, 계좌이체를 지원합니다"
            )
        );

        List<EvaluationService.EvaluationResult> results =
            evaluationService.runBatchEvaluation(testCases);

        assertThat(results).hasSize(2);
        for (var result : results) {
            assertThat(result.answer()).isNotBlank();
            assertThat(result.relevancyScore()).isBetween(0.0, 1.0);
        }
    }

    @Test
    @DisplayName("[통합] 인시던트 진단이 근본 원인을 파악한다")
    void incidentDiagnosisIdentifiesRootCause() {
        var diagnosis1 = evaluationService.diagnoseIncident(
            "전자제품 배송 기간이 어떻게 되나요?",
            "모든 주문에 무료 배송을 제공합니다"
        );
        assertThat(diagnosis1.rootCause()).isIn("retrieval", "generation");
        assertThat(diagnosis1.evidence()).isNotBlank();
        assertThat(diagnosis1.suggestedFix()).isNotBlank();

        var diagnosis2 = evaluationService.diagnoseIncident(
            "맞춤 제작 상품을 반품할 수 있나요?",
            "30일 이내에 반품할 수 있습니다"
        );
        assertThat(diagnosis2.rootCause()).isIn("retrieval", "generation");
        assertThat(diagnosis2.evidence()).isNotBlank();
        assertThat(diagnosis2.suggestedFix()).isNotBlank();
    }
}

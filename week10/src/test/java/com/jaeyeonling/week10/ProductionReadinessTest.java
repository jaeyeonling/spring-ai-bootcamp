package com.jaeyeonling.week10;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 최종 Week 10 프로젝트의 운영 준비성 테스트.
 *
 * 이 테스트들은 최소 운영 요구사항을 검증합니다:
 * 1. 애플리케이션 시작 및 헬스 엔드포인트 작동
 * 2. Actuator를 통해 메트릭 노출
 * 3. 핵심 컴포넌트들이 올바르게 연결됨
 *
 * 실행: ./gradlew :week10:test
 *
 * 사전 요구사항:
 *   - Qdrant 실행 중 (docker compose up -d qdrant)
 *   - OPENAI_API_KEY 설정됨
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductionReadinessTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("애플리케이션 컨텍스트가 성공적으로 로드된다")
    void contextLoads() {
        // 이 테스트가 통과하면 Spring Boot가 시작되고 모든 빈이 해결된 것
    }

    @Test
    @DisplayName("Actuator health 엔드포인트가 200을 반환한다")
    void healthEndpointReturns200() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").exists());
    }

    @Test
    @DisplayName("Actuator metrics 엔드포인트가 사용 가능한 메트릭 목록을 반환한다")
    void metricsEndpointListsMetrics() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.names").isArray());
    }

    // 아래 테스트들은 Week 10 기능을 구현한 후 통과해야 합니다.

    @Test
    @DisplayName("POST /api/chat이 출처와 함께 답변을 반환한다")
    void chatEndpointWorks() throws Exception {
        mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"question": "반품 정책이 어떻게 되나요?"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.answer").isNotEmpty())
            .andExpect(jsonPath("$.sources").isArray());
    }

    @Test
    @DisplayName("주문 상태 질문에 대해 툴 호출이 작동한다")
    void toolCallingWorks() throws Exception {
        mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"question": "주문 ORD-2024-001의 상태가 어떻게 되나요?"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.answer").isNotEmpty());
    }

    @Test
    @DisplayName("평가 파이프라인이 성공적으로 실행된다")
    void evaluationPipelineRuns() {
        // 테스트 질문에 대해 평가를 실행하고
        // 정확도가 최소 임계값을 충족하는지 검증합니다.
        // Week 10 구현에서 EvaluationRunner 또는 EvaluationService를 주입하여 테스트하세요.
    }
}

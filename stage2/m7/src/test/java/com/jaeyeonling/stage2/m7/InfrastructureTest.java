package com.jaeyeonling.stage2.m7;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M7 인프라 모듈 검증 테스트.
 *
 * 이 테스트들은 Docker 환경의 기본 동작을 검증합니다:
 * 1. 애플리케이션 컨텍스트 로드
 * 2. Actuator 헬스 엔드포인트
 * 3. 채팅 API 엔드포인트
 * 4. 프로파일별 설정 로드
 *
 * 실행: ./gradlew :stage2:m7:test
 *
 * 사전 요구사항:
 *   - Qdrant 실행 중 (docker compose up -d qdrant)
 *   - OPENAI_API_KEY 설정됨
 */
@SpringBootTest
@AutoConfigureMockMvc
class InfrastructureTest {

    @Autowired
    private MockMvc mockMvc;

    @Value("${spring.application.name}")
    private String applicationName;

    @Test
    @DisplayName("애플리케이션 컨텍스트가 성공적으로 로드된다")
    void contextLoads() {
        // 이 테스트가 통과하면 Spring Boot가 시작되고 모든 빈이 해결된 것
    }

    @Test
    @DisplayName("Actuator health 엔드포인트가 200을 반환한다")
    void healthEndpointReturns200() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists());
    }

    @Test
    @DisplayName("POST /api/chat이 정상 응답을 반환한다")
    void chatEndpointWorks() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question": "안녕하세요"}
                                """))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").isNotEmpty());
    }

    @Test
    @DisplayName("빈 질문은 400 Bad Request를 반환한다")
    void chatEndpointRejectsBadRequest() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question": ""}
                                """))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("애플리케이션 이름이 올바르게 설정된다")
    void applicationNameIsConfigured() {
        assert applicationName.equals("stage2-m7-infra");
    }
}

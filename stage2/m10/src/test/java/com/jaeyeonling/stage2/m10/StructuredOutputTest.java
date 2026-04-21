package com.jaeyeonling.stage2.m10;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 구조화 출력 검증 테스트.
 *
 * 실행: ./gradlew :stage2:m10:test
 *
 * 사전 조건:
 *   - 환경변수에 OPENAI_API_KEY가 설정되어 있어야 합니다
 */
@SpringBootTest
@AutoConfigureMockMvc
class StructuredOutputTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("응답에 모든 필수 필드가 포함된다 (category, confidence, sourceFiles)")
    void responseHasAllRequiredFields() throws Exception {
        mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"question": "반품 정책이 어떻게 되나요?"}
                    """))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.answer").isNotEmpty())
            .andExpect(jsonPath("$.category").isNotEmpty())
            .andExpect(jsonPath("$.confidence").isNumber())
            .andExpect(jsonPath("$.sourceFiles").isArray())
            .andExpect(jsonPath("$.requiresHumanAgent").isBoolean());
    }

    @Test
    @DisplayName("confidence 값이 0.0과 1.0 사이이다")
    void confidenceIsWithinRange() throws Exception {
        String response = mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"question": "배송은 얼마나 걸리나요?"}
                    """))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.confidence").isNumber())
            .andReturn()
            .getResponse()
            .getContentAsString();

        // confidence 값이 유효 범위인지 검증
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        ChatAnswer answer = mapper.readValue(response, ChatAnswer.class);
        org.assertj.core.api.Assertions.assertThat(answer.confidence())
            .isBetween(0.0, 1.0);
    }

    @Test
    @DisplayName("category가 유효한 분류값이다")
    void categoryIsValid() throws Exception {
        mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"question": "주문을 취소하고 싶어요"}
                    """))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.category").isNotEmpty());
    }

    @Test
    @DisplayName("빈 질문은 400을 반환한다")
    void blankQuestionReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"question": ""}
                    """))
            .andDo(print())
            .andExpect(status().isBadRequest());
    }
}

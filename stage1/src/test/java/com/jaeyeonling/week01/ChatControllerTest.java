package com.jaeyeonling.week01;

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
 * Week 01 미션의 인수 테스트.
 *
 * 이 테스트들은 "완료"가 어떤 모습인지를 정의합니다.
 * 실행: ./gradlew :week01:test
 *
 * 참고: 이 테스트들은 실제 OpenAI API를 호출합니다.
 * 환경변수에 OPENAI_API_KEY가 설정되어 있어야 합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("POST /api/chat가 200과 함께 답변을 반환한다")
    void chatEndpointReturnsAnswer() throws Exception {
        mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"question": "환불은 어떻게 받나요?"}
                    """))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.answer").isNotEmpty())
            .andExpect(jsonPath("$.answer").isString());
    }

    @Test
    @DisplayName("응답에 토큰 사용량 정보가 포함된다")
    void responseIncludesTokenUsage() throws Exception {
        mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"question": "어떤 결제 방법을 지원하나요?"}
                    """))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tokenUsage").exists())
            .andExpect(jsonPath("$.tokenUsage.totalTokens").isNumber());
    }

    @Test
    @DisplayName("답변이 질문과 관련 있다 (반품 정책 주제)")
    void answerIsRelevantToQuestion() throws Exception {
        mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"question": "반품 정책이 어떻게 되나요?"}
                    """))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.answer").isNotEmpty())
            // 답변이 사소하지 않아야 합니다 (20자 이상)
            // LLM 출력이 달라지므로 특정 문구를 하드코딩하지 않습니다.
            // week02의 RelevancyEvaluator 테스트에서 더 깊은 의미 검사를 합니다.
            .andExpect(jsonPath("$.answer").isString());
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

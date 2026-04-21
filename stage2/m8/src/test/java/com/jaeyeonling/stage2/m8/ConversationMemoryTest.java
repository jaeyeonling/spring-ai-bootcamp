package com.jaeyeonling.stage2.m8;

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
 * 멀티턴 대화 기억 검증 테스트.
 *
 * 실행: ./gradlew :stage2:m8:test
 *
 * 사전 조건:
 *   - 환경변수에 OPENAI_API_KEY가 설정되어 있어야 합니다
 */
@SpringBootTest
@AutoConfigureMockMvc
class ConversationMemoryTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("3턴 이상 대화에서 이전 맥락을 참조할 수 있다")
    void multiTurnConversationRetainsContext() throws Exception {
        String sessionId = "test-session-1";

        // 1턴: 주제 설정
        mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"sessionId": "%s", "question": "내 이름은 홍길동이야. 기억해줘."}
                    """.formatted(sessionId)))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.answer").isNotEmpty());

        // 2턴: 추가 정보
        mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"sessionId": "%s", "question": "나는 서울에 살고 있어."}
                    """.formatted(sessionId)))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.answer").isNotEmpty());

        // 3턴: 이전 맥락 참조 확인
        mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"sessionId": "%s", "question": "내 이름이 뭐라고 했지?"}
                    """.formatted(sessionId)))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.answer").value(
                org.hamcrest.Matchers.containsStringIgnoringCase("홍길동")
            ));
    }

    @Test
    @DisplayName("서로 다른 세션 ID는 대화 맥락을 공유하지 않는다")
    void differentSessionsAreIsolated() throws Exception {
        String sessionA = "session-A";
        String sessionB = "session-B";

        // 세션 A에서 정보 제공
        mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"sessionId": "%s", "question": "내 이름은 김철수야."}
                    """.formatted(sessionA)))
            .andDo(print())
            .andExpect(status().isOk());

        // 세션 B에서 해당 정보를 물어봄 — 알 수 없어야 함
        mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"sessionId": "%s", "question": "내 이름이 뭐야?"}
                    """.formatted(sessionB)))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.answer").value(
                org.hamcrest.Matchers.not(
                    org.hamcrest.Matchers.containsStringIgnoringCase("김철수")
                )
            ));
    }

    @Test
    @DisplayName("컨텍스트 윈도우 제한 — 대화가 길어져도 토큰 예산을 초과하지 않는다")
    void contextWindowLimitIsRespected() throws Exception {
        String sessionId = "test-session-window";

        // 슬라이딩 윈도우를 넘는 대화를 수행 (10턴 이상)
        for (int i = 1; i <= 12; i++) {
            mockMvc.perform(post("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"sessionId": "%s", "question": "이것은 %d번째 메시지입니다. 숫자 %d를 기억해주세요."}
                        """.formatted(sessionId, i, i * 100)))
                .andExpect(status().isOk());
        }

        // 가장 최근 대화에 대해 정상 응답이 오는지 확인 (토큰 초과 에러 없음)
        mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"sessionId": "%s", "question": "가장 최근에 내가 말한 숫자는 뭐야?"}
                    """.formatted(sessionId)))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.answer").isNotEmpty());
    }
}

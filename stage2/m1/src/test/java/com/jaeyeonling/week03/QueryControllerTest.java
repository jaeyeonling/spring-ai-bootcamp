package com.jaeyeonling.week03;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Week 03 미션의 통합 테스트.
 *
 * 이 테스트들은 "완료"가 어떤 모습인지를 정의합니다.
 * 실행: ./gradlew :week03:test
 *
 * 사전 조건:
 *   - Qdrant가 실행 중이어야 합니다 (docker-compose up -d qdrant)
 *   - 환경변수에 OPENAI_API_KEY가 설정되어 있어야 합니다
 *
 * 테스트 순서: 인제스트가 먼저 실행되고, 그 다음 쿼리가 실행됩니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class QueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @Order(1)
    @DisplayName("POST /api/ingest가 파일을 받아 청크 수를 반환한다")
    void ingestEndpointAcceptsFile() throws Exception {
        MockMultipartFile faqFile = new MockMultipartFile(
            "file",
            "test-faq.txt",
            MediaType.TEXT_PLAIN_VALUE,
            """
            ### 반품 정책이 어떻게 되나요?
            구매 후 30일 이내에 어떤 상품이든 전액 환불로 반품할 수 있습니다.
            상품은 원본 포장 상태이고 미사용 상태여야 합니다.

            ### 어떤 결제 방법을 지원하나요?
            초록 코퍼레이션은 신용카드(KB국민, 신한, 삼성), 카카오페이, 네이버페이, 무통장입금을 지원합니다.

            ### 배송은 얼마나 걸리나요?
            표준 배송은 영업일 기준 5-7일입니다. 익스프레스 배송은 1-2일입니다.

            ### 해외 배송이 가능한가요?
            네, 50개국 이상으로 배송합니다. 해외 배송은 10-14일이 소요됩니다.

            ### 고객 지원에 어떻게 연락하나요?
            support@cholog-corp.co.kr로 이메일을 보내시거나 1588-1234로 전화하세요.
            """.getBytes()
        );

        mockMvc.perform(multipart("/api/ingest").file(faqFile))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.filename").value("test-faq.txt"))
            .andExpect(jsonPath("$.chunks").isNumber())
            .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @Order(2)
    @DisplayName("POST /api/query가 소스와 함께 관련 답변을 반환한다")
    void queryReturnsRelevantAnswer() throws Exception {
        mockMvc.perform(post("/api/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"question": "반품 정책이 어떻게 되나요?"}
                    """))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.answer").isNotEmpty())
            .andExpect(jsonPath("$.sources").isArray())
            .andExpect(jsonPath("$.tokenUsage").exists())
            .andExpect(jsonPath("$.tokenUsage.totalTokens").isNumber());
    }

    @Test
    @Order(3)
    @DisplayName("답변에 30일 언급이 포함된다 (FAQ 내용에서)")
    void answerContainsRelevantContent() throws Exception {
        mockMvc.perform(post("/api/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"question": "상품을 반품할 수 있는 기간은 며칠인가요?"}
                    """))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.answer").value(
                org.hamcrest.Matchers.containsStringIgnoringCase("30")
            ));
    }

    @Test
    @Order(4)
    @DisplayName("빈 질문은 400을 반환한다")
    void blankQuestionReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"question": ""}
                    """))
            .andDo(print())
            .andExpect(status().isBadRequest());
    }

    @Test
    @Order(5)
    @DisplayName("빈 파일 업로드는 400을 반환한다")
    void emptyFileReturnsBadRequest() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
            "file",
            "empty.txt",
            MediaType.TEXT_PLAIN_VALUE,
            new byte[0]
        );

        mockMvc.perform(multipart("/api/ingest").file(emptyFile))
            .andDo(print())
            .andExpect(status().isBadRequest());
    }
}

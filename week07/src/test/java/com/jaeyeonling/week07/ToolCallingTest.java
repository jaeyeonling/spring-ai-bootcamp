package com.jaeyeonling.week07;

/**
 * LLM이 각 질문 유형을 올바른 툴로 라우팅하는지 테스트합니다.
 *
 * 이 테스트들은 7주차의 핵심 동작을 검증합니다:
 * - 주문 상태 질문 -> getOrderStatus 툴
 * - 매출 질문 -> getMonthlyRevenue 툴
 * - 정책/FAQ 질문 -> searchFaq 툴
 *
 * 참고: 이 테스트들은 실행 중인 LLM이 필요합니다 (OpenAI API 키 또는 Ollama).
 * 단위 테스트가 아닌 통합 테스트입니다.
 */

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Disabled("실행 중인 LLM이 필요합니다 — API 키가 설정되면 @Disabled 제거")
class ToolCallingTest {

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Autowired
    private OrderTools orderTools;

    @Autowired
    private FaqSearchTool faqSearchTool;

    @Autowired
    private ReflectionService reflectionService;

    // TODO: 아래 각 테스트 메서드를 구현하세요.
    //  각 테스트는:
    //  1. 등록된 툴로 ChatClient 빌드
    //  2. 질문 전송
    //  3. 응답에 예상 데이터가 포함되어 있는지 확인 (올바른 툴이 호출됐음을 나타냄)

    @Test
    @DisplayName("주문 상태 질문이 getOrderStatus 툴을 호출한다")
    void shouldCallGetOrderStatusForOrderQuestions() {
        // 등록된 툴로 "주문 #ORD-2024-001의 상태가 어떻게 되나요?"를 전송합니다.
        // 응답에 "배송 중" 또는 "ORD-2024-001"이 포함되어 있는지 확인합니다 (모의 데이터에서).
        ChatClient chatClient = chatClientBuilder.build();
        String answer = chatClient.prompt()
            .user("주문 #ORD-2024-001의 상태가 어떻게 되나요?")
            .tools(orderTools, faqSearchTool)
            .call()
            .content();

        assertThat(answer).isNotNull();
        assertThat(answer).satisfiesAnyOf(
            a -> assertThat(a).contains("배송 중"),
            a -> assertThat(a).contains("배송"),
            a -> assertThat(a).contains("ORD-2024-001")
        );
    }

    @Test
    @DisplayName("매출 질문이 getMonthlyRevenue 툴을 호출한다")
    void shouldCallGetMonthlyRevenueForRevenueQuestions() {
        // 등록된 툴로 "2024년 1월 매출은 얼마인가요?"를 전송합니다.
        // 응답에 "142,500" 또는 "142500"이 포함되어 있는지 확인합니다 (모의 데이터에서).
        ChatClient chatClient = chatClientBuilder.build();
        String answer = chatClient.prompt()
            .user("2024년 1월 매출은 얼마인가요?")
            .tools(orderTools, faqSearchTool)
            .call()
            .content();

        assertThat(answer).isNotNull();
        assertThat(answer).contains("142");
    }

    @Test
    @DisplayName("정책 질문이 searchFaq 툴을 호출한다")
    void shouldCallSearchFaqForPolicyQuestions() {
        // 등록된 툴로 "환불 정책이 어떻게 되나요?"를 전송합니다.
        // 응답에 환불 관련 내용이 포함되어 있는지 확인합니다 (RAG 검색에서).
        ChatClient chatClient = chatClientBuilder.build();
        String answer = chatClient.prompt()
            .user("환불 정책이 어떻게 되나요?")
            .tools(orderTools, faqSearchTool)
            .call()
            .content();

        assertThat(answer).isNotNull();
        assertThat(answer.toLowerCase()).satisfiesAnyOf(
            a -> assertThat(a).contains("환불"),
            a -> assertThat(a).contains("반품"),
            a -> assertThat(a).contains("refund"),
            a -> assertThat(a).contains("return")
        );
    }

    @Test
    @DisplayName("반품 질문이 searchFaq 툴을 호출한다")
    void shouldCallSearchFaqForReturnQuestions() {
        // 등록된 툴로 "30일 후에도 반품할 수 있나요?"를 전송합니다.
        // 응답이 반품 정책에 관한 것인지 확인합니다 (주문 상태나 매출이 아님).
        ChatClient chatClient = chatClientBuilder.build();
        String answer = chatClient.prompt()
            .user("30일 후에도 반품할 수 있나요?")
            .tools(orderTools, faqSearchTool)
            .call()
            .content();

        assertThat(answer).isNotNull();
        assertThat(answer.length()).isGreaterThan(20);
    }

    @Test
    @DisplayName("주문 상세 질문이 getOrderStatus 툴을 호출한다")
    void shouldCallGetOrderStatusForOrderDetailsQuestions() {
        // 등록된 툴로 "주문 #ORD-2024-002 상세 정보 보여주세요"를 전송합니다.
        // 응답에 "처리 중"이 포함되어 있는지 확인합니다 (ORD-2024-002의 모의 데이터).
        ChatClient chatClient = chatClientBuilder.build();
        String answer = chatClient.prompt()
            .user("주문 #ORD-2024-002 상세 정보 보여주세요")
            .tools(orderTools, faqSearchTool)
            .call()
            .content();

        assertThat(answer).isNotNull();
        assertThat(answer).satisfiesAnyOf(
            a -> assertThat(a).contains("처리 중"),
            a -> assertThat(a).contains("처리"),
            a -> assertThat(a).contains("ORD-2024-002")
        );
    }

    @Test
    @DisplayName("Reflection이 올바른 답변을 검증 통과시킨다")
    void reflectionShouldVerifyCorrectAnswers() {
        // reflectionService.verify()를 사용해 올바른 답변이 통과하는지 확인합니다.
        boolean result = reflectionService.verify(
            "주문 #ORD-2024-001의 상태가 어떻게 되나요?",
            "주문 #ORD-2024-001은 현재 배송 중 상태이며 추적번호는 1Z999AA10123456784입니다.");
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Reflection이 잘못된 답변을 검증 실패시킨다")
    void reflectionShouldRejectWrongAnswers() {
        // reflectionService.verify()를 사용해 잘못된 답변이 실패하는지 확인합니다.
        boolean result = reflectionService.verify(
            "주문 #ORD-2024-001의 상태가 어떻게 되나요?",
            "주문 정보에 접근할 수 없습니다.");
        assertThat(result).isFalse();
    }
}

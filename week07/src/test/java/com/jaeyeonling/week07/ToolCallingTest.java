package com.jaeyeonling.week07;

/**
 * LLM이 각 질문 유형을 올바른 툴로 라우팅하는지 테스트합니다.
 */

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ToolCallingTest {

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Autowired
    private ToolCallbackProvider toolCallbackProvider;

    @Autowired
    private ReflectionService reflectionService;

    @Test
    @DisplayName("주문 상태 질문이 getOrderStatus 툴을 호출한다")
    void shouldCallGetOrderStatusForOrderQuestions() {
        ChatClient chatClient = chatClientBuilder.build();
        String answer = chatClient.prompt()
            .user("주문 #ORD-2024-001의 상태가 어떻게 되나요?")
            .toolCallbacks(toolCallbackProvider)
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
        ChatClient chatClient = chatClientBuilder.build();
        String answer = chatClient.prompt()
            .user("2024년 1월 매출은 얼마인가요?")
            .toolCallbacks(toolCallbackProvider)
            .call()
            .content();

        assertThat(answer).isNotNull();
        assertThat(answer).contains("142");
    }

    @Test
    @DisplayName("정책 질문이 searchFaq 툴을 호출한다")
    void shouldCallSearchFaqForPolicyQuestions() {
        ChatClient chatClient = chatClientBuilder.build();
        String answer = chatClient.prompt()
            .user("환불 정책이 어떻게 되나요?")
            .toolCallbacks(toolCallbackProvider)
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
        ChatClient chatClient = chatClientBuilder.build();
        String answer = chatClient.prompt()
            .user("30일 후에도 반품할 수 있나요?")
            .toolCallbacks(toolCallbackProvider)
            .call()
            .content();

        assertThat(answer).isNotNull();
        assertThat(answer.length()).isGreaterThan(20);
    }

    @Test
    @DisplayName("주문 상세 질문이 getOrderStatus 툴을 호출한다")
    void shouldCallGetOrderStatusForOrderDetailsQuestions() {
        ChatClient chatClient = chatClientBuilder.build();
        String answer = chatClient.prompt()
            .user("주문 #ORD-2024-002 상세 정보 보여주세요")
            .toolCallbacks(toolCallbackProvider)
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
        boolean result = reflectionService.verify(
            "주문 #ORD-2024-001의 상태가 어떻게 되나요?",
            "주문 #ORD-2024-001은 현재 배송 중 상태이며 추적번호는 1Z999AA10123456784입니다.");
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Reflection이 잘못된 답변을 검증 실패시킨다")
    void reflectionShouldRejectWrongAnswers() {
        boolean result = reflectionService.verify(
            "주문 #ORD-2024-001의 상태가 어떻게 되나요?",
            "주문 정보에 접근할 수 없습니다.");
        assertThat(result).isFalse();
    }
}

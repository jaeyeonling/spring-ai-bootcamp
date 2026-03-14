package com.jaeyeonling.week09;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Week 09 테스트 — MCP 서버 툴/리소스/프롬프트 등록.
 *
 * @Tool 메서드는 Spring 빈의 일반 Java 메서드이므로
 * MCP 프로토콜 레이어 없이 직접 호출하여 검증합니다.
 *
 * 실행: ./gradlew :week09:test
 *
 * 참고: OPENAI_API_KEY와 실행 중인 Qdrant 인스턴스가 필요합니다.
 */
@SpringBootTest
class McpServerTest {

    @Autowired
    private FaqMcpService faqMcpService;

    @Autowired
    private OrderMcpService orderMcpService;

    @Test
    @DisplayName("FaqMcpService가 주입 가능하다")
    void faqMcpServiceIsInjectable() {
        assertThat(faqMcpService).isNotNull();
    }

    @Test
    @DisplayName("OrderMcpService가 주입 가능하다")
    void orderMcpServiceIsInjectable() {
        assertThat(orderMcpService).isNotNull();
    }

    @Test
    @DisplayName("MCP 서버 설정으로 애플리케이션 컨텍스트가 로드된다")
    void applicationContextLoads() {
        assertThat(faqMcpService).isNotNull();
        assertThat(orderMcpService).isNotNull();
    }

    @Test
    @DisplayName("searchFaq가 유효한 쿼리에 대해 결과를 반환한다")
    void searchFaqReturnsResults() {
        List<Map<String, Object>> results = faqMcpService.searchFaq("환불 정책", 3);
        assertThat(results).isNotEmpty();
        assertThat(results).hasSizeLessThanOrEqualTo(3);
    }

    @Test
    @DisplayName("faqCategories가 카테고리 목록을 반환한다")
    void faqCategoriesReturnsCategories() {
        List<Map<String, String>> categories = faqMcpService.faqCategories();
        assertThat(categories).isNotEmpty();
        assertThat(categories).anyMatch(c -> c.containsKey("id") && c.containsKey("title"));
    }

    @Test
    @DisplayName("customerSupportPrompt가 프롬프트 템플릿을 반환한다")
    void customerSupportPromptReturnsTemplate() {
        Map<String, String> prompt = faqMcpService.customerSupportPrompt("환불 요청");
        assertThat(prompt).containsKey("template");
        assertThat(prompt.get("template")).contains("환불 요청");
    }

    @Test
    @DisplayName("lookupOrder가 알려진 주문 ID에 대해 상세 데이터를 반환한다")
    void lookupOrderReturnsStubData() {
        Map<String, Object> order = orderMcpService.lookupOrder("ORD-2024-001");
        assertThat(order).containsKey("orderId");
        assertThat(order.get("status")).isNotNull();
        assertThat(order.get("status")).isEqualTo("배송 완료");
    }

    @Test
    @DisplayName("lookupOrder가 알 수 없는 주문 ID에 대해 에러를 반환한다")
    void lookupOrderReturnsErrorForUnknownOrder() {
        Map<String, Object> order = orderMcpService.lookupOrder("ORD-9999-999");
        assertThat(order).containsKey("error");
    }

    @Test
    @DisplayName("checkOrderStatus가 상태와 예상 배송일을 반환한다")
    void checkOrderStatusReturnsStatus() {
        Map<String, String> status = orderMcpService.checkOrderStatus("ORD-2024-003");
        assertThat(status.get("status")).isEqualTo("배송 중");
        assertThat(status.get("estimatedDelivery")).isNotBlank();
    }
}

package com.jaeyeonling.week09;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Week 09 테스트 — MCP 서버 툴/리소스/프롬프트 등록.
 *
 * 이 테스트들은 MCP 서버가 올바르게 시작되고
 * 어노테이션된 서비스들이 올바르게 등록되었는지 검증합니다.
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
        // 이 테스트가 통과하면 MCP 서버 자동 설정이
        // 모든 어노테이션된 서비스를 감지하고 등록한 것
        assertThat(faqMcpService).isNotNull();
        assertThat(orderMcpService).isNotNull();
    }

    @Test
    @Disabled("@Tool 메서드를 구현한 후 활성화하세요. Qdrant가 실행 중이고 FAQ 데이터가 인제스트된 상태에서 실행하세요.")
    @DisplayName("searchFaq가 유효한 쿼리에 대해 결과를 반환한다")
    void searchFaqReturnsResults() {
        // TODO: @Tool 메서드 구현 후 아래 주석 해제
        // List<Map<String, Object>> results = faqMcpService.searchFaq("환불 정책", 3);
        // assertThat(results).isNotEmpty();
        // assertThat(results).hasSizeLessThanOrEqualTo(3);
    }

    @Test
    @Disabled("@Tool 메서드를 구현한 후 활성화하세요.")
    @DisplayName("lookupOrder가 알려진 주문 ID에 대해 스텁 데이터를 반환한다")
    void lookupOrderReturnsStubData() {
        // TODO: @Tool 메서드 구현 후 아래 주석 해제
        // Map<String, Object> order = orderMcpService.lookupOrder("ORD-12345");
        // assertThat(order).containsKey("orderId");
        // assertThat(order.get("status")).isNotNull();
    }
}

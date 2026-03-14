package com.jaeyeonling.week10;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Spring AI의 @Tool 어노테이션을 통해 LLM에 노출되는 주문 관련 툴.
 *
 * 각 @Tool 메서드는 LLM이 호출할 수 있는 "함수"가 됩니다.
 * 모의 데이터를 반환합니다 — 실제 운영에서는 실제 주문 API를 호출합니다.
 */
@Service
public class OrderTools {

    private static final Logger log = LoggerFactory.getLogger(OrderTools.class);

    // 모의 주문 데이터
    private static final Map<String, OrderStatus> MOCK_ORDERS = Map.of(
        "ORD-2024-001", new OrderStatus(
            "ORD-2024-001", "배송 중", "1Z999AA10123456784",
            LocalDateTime.of(2024, 1, 15, 10, 30)),
        "ORD-2024-002", new OrderStatus(
            "ORD-2024-002", "처리 중", null,
            LocalDateTime.of(2024, 1, 20, 14, 0)),
        "ORD-2024-003", new OrderStatus(
            "ORD-2024-003", "배송 완료", "1Z999AA10987654321",
            LocalDateTime.of(2024, 1, 10, 9, 15))
    );

    // 모의 매출 데이터
    private static final Map<String, BigDecimal> MOCK_REVENUE = Map.of(
        "2024-01", new BigDecimal("142500.00"),
        "2024-02", new BigDecimal("158300.00"),
        "2024-03", new BigDecimal("171200.00"),
        "2023-12", new BigDecimal("198700.00")
    );

    /**
     * 고객 주문의 현재 상태를 가져옵니다.
     *
     * @param orderId 주문 ID (예: "ORD-2024-001")
     * @return 배송 정보가 포함된 주문 상태
     */
    @Tool(description = "주문 ID로 고객 주문의 현재 상태를 조회합니다. " +
                         "주문 상태, 추적 번호, 마지막 업데이트 시간을 반환합니다.")
    public OrderStatus getOrderStatus(
            @ToolParam(description = "주문 ID, 예: ORD-2024-001") String orderId) {

        log.info("툴 호출됨: getOrderStatus(orderId={})", orderId);

        OrderStatus status = MOCK_ORDERS.get(orderId);
        if (status == null) {
            return new OrderStatus(orderId, "찾을 수 없음",
                null, LocalDateTime.now());
        }
        return status;
    }

    /**
     * 특정 월의 총 매출을 가져옵니다.
     *
     * @param year  연도 (예: 2024)
     * @param month 월 (1-12)
     * @return 해당 월의 매출 데이터
     */
    @Tool(description = "특정 월의 총 매출을 조회합니다. " +
                         "USD 단위의 매출 금액을 반환합니다.")
    public RevenueReport getMonthlyRevenue(
            @ToolParam(description = "연도, 예: 2024") int year,
            @ToolParam(description = "월 번호 (1-12), 예: 1월은 1") int month) {

        log.info("툴 호출됨: getMonthlyRevenue(year={}, month={})", year, month);

        String key = "%d-%02d".formatted(year, month);
        BigDecimal revenue = MOCK_REVENUE.getOrDefault(key, BigDecimal.ZERO);

        return new RevenueReport(year, month, revenue, "USD");
    }

    // --- 응답 레코드 ---

    public record OrderStatus(
        String orderId,
        String status,
        String trackingNumber,
        LocalDateTime lastUpdated
    ) {}

    public record RevenueReport(
        int year,
        int month,
        BigDecimal totalRevenue,
        String currency
    ) {}
}

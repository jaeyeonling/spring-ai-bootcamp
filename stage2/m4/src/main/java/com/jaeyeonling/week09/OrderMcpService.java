package com.jaeyeonling.week09;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * MCP 호환 클라이언트에 주문 관련 툴을 노출하는 MCP 서비스.
 *
 * 실제 애플리케이션에서는 주문 데이터베이스에 연결합니다.
 * 이 실습에서는 MCP 툴 패턴을 시연하기 위해 스텁 데이터를 사용합니다.
 */
@Service
public class OrderMcpService {

    private static final Map<String, Map<String, Object>> ORDERS = Map.of(
        "ORD-2024-001", Map.of(
            "orderId",   "ORD-2024-001",
            "status",    "배송 완료",
            "items",     List.of("무선 키보드", "USB-C 허브"),
            "total",     89500,
            "createdAt", "2024-01-15T10:30:00Z"
        ),
        "ORD-2024-002", Map.of(
            "orderId",   "ORD-2024-002",
            "status",    "처리 중",
            "items",     List.of("노트북 거치대"),
            "total",     45000,
            "createdAt", "2024-01-18T14:20:00Z"
        ),
        "ORD-2024-003", Map.of(
            "orderId",   "ORD-2024-003",
            "status",    "배송 중",
            "items",     List.of("모니터 암", "마우스 패드"),
            "total",     127000,
            "createdAt", "2024-01-20T09:15:00Z"
        )
    );

    @Tool(description = "ID로 주문을 조회합니다. " +
                        "상태, 상품, 총액, 생성 날짜를 포함한 주문 상세 정보를 반환합니다. " +
                        "고객이 주문 상세 정보를 요청할 때 사용하세요.")
    public Map<String, Object> lookupOrder(
            @ToolParam(description = "주문 ID, 예: ORD-2024-001") String orderId) {
        if (ORDERS.containsKey(orderId)) {
            return ORDERS.get(orderId);
        }
        return Map.of(
            "orderId", orderId,
            "error",   "주문을 찾을 수 없습니다"
        );
    }

    @Tool(description = "주문의 현재 상태를 확인합니다. " +
                        "상태와 예상 배송일을 반환합니다. " +
                        "고객이 배송 상태만 간단히 확인하고 싶을 때 사용하세요. lookupOrder보다 가벼운 조회입니다.")
    public Map<String, String> checkOrderStatus(
            @ToolParam(description = "확인할 주문 ID, 예: ORD-2024-001") String orderId) {
        if (ORDERS.containsKey(orderId)) {
            String status = (String) ORDERS.get(orderId).get("status");
            String delivery = switch (status) {
                case "배송 완료" -> "배송 완료";
                case "배송 중" -> "2024-01-25 예정";
                case "처리 중" -> "2024-01-28 예정";
                default -> "미정";
            };
            return Map.of(
                "orderId",           orderId,
                "status",            status,
                "estimatedDelivery", delivery
            );
        }
        return Map.of(
            "orderId", orderId,
            "status",  "주문을 찾을 수 없습니다",
            "estimatedDelivery", "N/A"
        );
    }
}

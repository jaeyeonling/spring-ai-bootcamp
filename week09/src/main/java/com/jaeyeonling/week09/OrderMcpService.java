package com.jaeyeonling.week09;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * MCP 호환 클라이언트에 주문 관련 툴을 노출하는 MCP 서비스.
 *
 * 실제 애플리케이션에서는 주문 데이터베이스 또는 서비스에 연결합니다.
 * 이 실습에서는 MCP 툴 패턴을 시연하기 위해 스텁 데이터를 사용합니다.
 *
 * Spring AI 1.1.2는 org.springframework.ai.tool.annotation의
 * @Tool과 @ToolParam 어노테이션을 사용합니다.
 */
@Service
public class OrderMcpService {

    // TODO: lookupOrder 툴을 구현하세요.
    //  @Tool로 어노테이션을 달고 명확한 설명을 제공하세요.
    //
    //  툴이 해야 할 일:
    //    - @ToolParam으로 어노테이션된 orderId (String) 파라미터를 받음
    //    - 주문 상세 정보를 Map으로 반환 (orderId, status, items, total, createdAt)
    //    - 이 실습에서는 orderId에 기반한 스텁 데이터를 반환
    //
    //  예시:
    //    @Tool(description = "ID로 주문을 조회합니다. 상태, 상품, 총액, 생성 날짜를 포함한 주문 상세 정보를 반환합니다.")
    //    public Map<String, Object> lookupOrder(
    //            @ToolParam(description = "주문 ID (예: ORD-12345)") String orderId) {
    //        // 스텁 구현 — 실제 데이터베이스 조회로 교체
    //        return Map.of(
    //            "orderId",   orderId,
    //            "status",    "배송 중",
    //            "items",     List.of("위젯 A", "위젯 B"),
    //            "total",     59.99,
    //            "createdAt", "2025-01-15T10:30:00Z"
    //        );
    //    }

    // TODO: checkOrderStatus 툴을 구현하세요.
    //  @Tool로 어노테이션을 달고 명확한 설명을 제공하세요.
    //
    //  툴이 해야 할 일:
    //    - @ToolParam으로 어노테이션된 orderId (String) 파라미터를 받음
    //    - 간소화된 상태 응답 반환 (orderId, status, estimatedDelivery)
    //    - 이것은 lookupOrder보다 가벼운 툴 — 상태만 필요할 때 사용
    //
    //  예시:
    //    @Tool(description = "주문의 현재 상태를 확인합니다. 상태와 예상 배송일을 반환합니다. 빠른 상태 확인에 사용하세요.")
    //    public Map<String, String> checkOrderStatus(
    //            @ToolParam(description = "확인할 주문 ID (예: ORD-12345)") String orderId) {
    //        return Map.of(
    //            "orderId",           orderId,
    //            "status",            "배송 중",
    //            "estimatedDelivery", "2025-01-20"
    //        );
    //    }
}

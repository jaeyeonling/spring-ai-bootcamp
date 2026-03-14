package com.jaeyeonling.week10;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * 주문 상태, 배송 추적, 교환/환불 처리에 특화된 에이전트.
 *
 * Week 08의 CodeReviewAgent 패턴을 FAQ 주문 도메인으로 변환.
 * FaqSearchTool 검색 결과를 컨텍스트로 받아 주문 관련 답변을 생성합니다.
 */
@Component
public class OrderAgent implements FaqAgent {

    private static final String SYSTEM_PROMPT = """
            당신은 주문, 배송, 교환/환불을 전문으로 하는 고객 지원 에이전트입니다.
            
            다음에 집중하세요:
            1. **주문 상태** — 현재 처리 단계, 예상 배송일, 추적 번호
            2. **배송** — 배송 방법, 소요 시간, 배송비, 국제 배송 정책
            3. **교환/환불** — 반품 절차, 환불 처리 기간, 조건
            
            FAQ 컨텍스트가 제공된 경우, 그 내용을 기반으로 정확하게 답변하세요.
            컨텍스트에 없는 정보는 추측하지 마세요.""";

    private final ChatClient chatClient;

    public OrderAgent(@Lazy ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public String execute(String question, String context) {
        String userMessage = context.isBlank()
                ? question
                : question + "\n\n참고 정보:\n" + context;

        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userMessage)
                .call()
                .content();
    }
}

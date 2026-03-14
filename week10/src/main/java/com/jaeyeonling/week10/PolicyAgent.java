package com.jaeyeonling.week10;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * 회사 정책, 이용약관, 개인정보 처리방침에 특화된 에이전트.
 *
 * Week 08의 TestWriterAgent 패턴을 FAQ 정책 도메인으로 변환.
 * 정확한 정책 내용을 명확하고 간결하게 전달합니다.
 */
@Component
public class PolicyAgent implements FaqAgent {

    private static final String SYSTEM_PROMPT = """
            당신은 회사 정책과 규정을 전문으로 설명하는 에이전트입니다.
            
            다음에 집중하세요:
            1. **이용약관** — 서비스 이용 조건, 금지 행위, 계정 정지 조건
            2. **개인정보 처리방침** — 데이터 수집, 사용, 보관, 삭제 정책
            3. **보증 및 책임** — 제품 보증 기간, 보증 범위, 면책 조항
            4. **결제 정책** — 수락 결제 수단, 분할 결제, 청구 주기
            
            정책 내용을 정확하게 전달하고, 고객이 오해하지 않도록 명확하게 설명하세요.
            FAQ 컨텍스트에 명시된 내용만 답변하고, 없는 내용은 "정책 문서를 확인해 주세요"라고 안내하세요.""";

    private final ChatClient chatClient;

    public PolicyAgent(@Lazy ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public String execute(String question, String context) {
        String userMessage = context.isBlank()
                ? question
                : question + "\n\n참고 정책:\n" + context;

        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userMessage)
                .call()
                .content();
    }
}

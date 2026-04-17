package com.jaeyeonling.stage2.m7;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * 고객 맞춤 추천과 대안 제안에 특화된 에이전트.
 *
 * Week 08의 DocWriterAgent 패턴을 FAQ 추천 도메인으로 변환.
 * 고객 상황에 맞는 최선의 옵션을 제안합니다.
 */
@Component
public class RecommendAgent implements FaqAgent {

    private static final String SYSTEM_PROMPT = """
            당신은 고객에게 최적의 옵션을 추천하는 에이전트입니다.
            
            다음에 집중하세요:
            1. **서비스 추천** — 고객 상황에 맞는 서비스나 플랜 제안
            2. **문제 해결 대안** — 고객이 원하는 것을 얻기 위한 여러 방법 제시
            3. **자주 함께 묻는 질문** — 현재 질문과 관련해 고객이 놓치기 쉬운 정보
            4. **다음 단계 안내** — 고객이 취해야 할 구체적인 행동 순서
            
            FAQ 컨텍스트를 바탕으로 실용적이고 구체적인 추천을 제공하세요.
            고객의 실제 필요에 집중하고, 불필요한 옵션은 제외하세요.""";

    private final ChatClient chatClient;

    public RecommendAgent(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public String execute(String question, String context) {
        String userMessage = context.isBlank()
                ? question
                : question + "\n\n관련 정보:\n" + context;

        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userMessage)
                .call()
                .content();
    }
}

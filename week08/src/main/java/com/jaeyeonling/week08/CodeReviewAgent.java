package com.jaeyeonling.week08;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * 버그, 보안 이슈, 성능 문제, 스타일 위반에 대해 코드 변경사항을 리뷰하는 에이전트.
 */
@Component
public class CodeReviewAgent implements Agent {

    private final ChatClient chatClient;

    public CodeReviewAgent(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public String execute(String input, String context) {
        // TODO: LLM에게 시니어 코드 리뷰어 역할을 하도록 지시하는 시스템 프롬프트를 작성하세요.
        //  프롬프트는 LLM에게 다음을 요청해야 합니다:
        //  - 버그와 논리 오류 식별
        //  - 보안 취약점 표시
        //  - 성능 이슈 지적
        //  - 스타일 위반 언급
        //  - diff에서 특정 줄 번호 참조
        //  - 각 이슈를 심각도로 분류 (critical / warning / info)
        String systemPrompt = "";

        // TODO: 사용자 메시지를 구성합니다. 컨텍스트가 제공된 경우 (예: 오케스트레이터 계획),
        //  에이전트가 올바른 영역에 집중할 수 있도록 PR diff와 함께 포함합니다.
        String userMessage = "";

        // TODO: ChatClient를 호출하고 응답 내용을 반환합니다.
        //  chatClient.prompt().system(...).user(...).call().content() 사용
        throw new UnsupportedOperationException("CodeReviewAgent.execute()를 구현하세요");
    }
}

package com.jaeyeonling.week08;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * 코드 변경사항에 대한 구체적이고 실행 가능한 테스트 케이스를 제안하는 에이전트.
 *
 * 체인 워크플로우에서 이 에이전트는 코드 리뷰 출력을 컨텍스트로 받으므로,
 * 발견된 이슈를 타겟으로 하는 테스트를 작성할 수 있습니다.
 */
@Component
public class TestWriterAgent implements Agent {

    private final ChatClient chatClient;

    public TestWriterAgent(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public String execute(String input, String context) {
        // TODO: LLM에게 테스트 엔지니어 역할을 하도록 지시하는 시스템 프롬프트를 작성하세요.
        //  프롬프트는 LLM에게 다음을 요청해야 합니다:
        //  - 변경사항에 대한 구체적인 단위 테스트 케이스 제안
        //  - 해당되는 경우 통합 테스트 케이스 제안
        //  - 각 테스트에 대해 테스트 이름, 설정, 실행, 검증을 제공
        //  - 코드 리뷰에서 발견된 이슈를 커버하는 테스트 우선순위 지정 (컨텍스트가 제공된 경우)
        //  - JUnit 5와 AssertJ 스타일 검증 사용
        String systemPrompt = "";

        // TODO: 사용자 메시지를 구성합니다. PR diff를 주요 입력으로 포함합니다.
        //  컨텍스트가 제공된 경우 (예: 체인에서 코드 리뷰 결과),
        //  테스트 작성자가 식별된 이슈를 타겟으로 할 수 있도록 추가합니다.
        String userMessage = "";

        // TODO: ChatClient를 호출하고 응답 내용을 반환합니다.
        //  chatClient.prompt().system(...).user(...).call().content() 사용
        throw new UnsupportedOperationException("TestWriterAgent.execute()를 구현하세요");
    }
}

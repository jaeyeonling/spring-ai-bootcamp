package com.jaeyeonling.week08;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * 코드 변경사항을 바탕으로 문서 업데이트를 제안하는 에이전트.
 *
 * 체인 워크플로우에서 이 에이전트는 코드 리뷰와 테스트 제안을
 * 컨텍스트로 받으므로, 전체적인 그림을 반영하는 문서를 추천할 수 있습니다.
 */
@Component
public class DocWriterAgent implements Agent {

    private final ChatClient chatClient;

    public DocWriterAgent(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public String execute(String input, String context) {
        // TODO: LLM에게 기술 문서 작성자 역할을 하도록 지시하는 시스템 프롬프트를 작성하세요.
        //  프롬프트는 LLM에게 다음을 요청해야 합니다:
        //  - 업데이트가 필요한 문서 파일 식별
        //  - 각 문서에 대한 구체적인 변경 사항 제안 (섹션, 추가/수정 내용)
        //  - README, API 문서, Javadoc, 아키텍처 문서 고려
        //  - 컨텍스트에 코드 리뷰와 테스트 제안이 포함된 경우 해당 인사이트 반영
        //  - 문서가 필요한 새로운 공개 API 표시
        String systemPrompt = "";

        // TODO: 사용자 메시지를 구성합니다. PR diff를 주요 입력으로 포함합니다.
        //  컨텍스트가 제공된 경우 (예: 체인에서 리뷰 + 테스트),
        //  문서 작성자가 전체적인 그림을 파악할 수 있도록 추가합니다.
        String userMessage = "";

        // TODO: ChatClient를 호출하고 응답 내용을 반환합니다.
        //  chatClient.prompt().system(...).user(...).call().content() 사용
        throw new UnsupportedOperationException("DocWriterAgent.execute()를 구현하세요");
    }
}

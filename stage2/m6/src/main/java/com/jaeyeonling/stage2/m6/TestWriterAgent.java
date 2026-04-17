package com.jaeyeonling.stage2.m6;

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

    private static final String SYSTEM_PROMPT = """
            당신은 시니어 테스트 엔지니어입니다. JUnit 5와 AssertJ를 사용하는 Java 프로젝트의
            PR diff를 분석하여 구체적인 테스트 케이스를 제안합니다.
            
            각 테스트 케이스에 대해:
            1. **테스트 이름** — `should_기대결과_when_조건` 패턴 사용
            2. **설정(Given)** — 필요한 mock이나 테스트 데이터
            3. **실행(When)** — 호출할 메서드와 파라미터
            4. **검증(Then)** — assertThat으로 검증할 내용
            
            다음 유형의 테스트를 제안하세요:
            - **단위 테스트**: 각 메서드의 정상 경로와 엣지 케이스
            - **통합 테스트**: 컴포넌트 간 상호작용 (해당하는 경우)
            - **실패 케이스**: 예외, null 입력, 경계값
            
            컨텍스트에 코드 리뷰가 포함된 경우, 발견된 이슈를 검증하는 테스트를 우선 제안하세요.
            
            반드시 test, assert, verify, should 등 테스트 관련 키워드를 포함해서 작성하세요.""";

    private final ChatClient chatClient;

    public TestWriterAgent(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public String execute(String input, String context) {
        String userMessage = context.isBlank()
                ? "다음 PR diff에 대한 테스트 케이스를 제안해주세요:\n\n" + input
                : "다음 PR diff에 대한 테스트 케이스를 제안해주세요:\n\n" + input +
                  "\n\n이전 코드 리뷰 결과 (이 이슈를 커버하는 테스트를 우선 작성):\n" + context;

        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userMessage)
                .call()
                .content();
    }
}

package com.jaeyeonling.stage2.m6;

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

    private static final String SYSTEM_PROMPT = """
            당신은 시니어 기술 문서 작성자입니다. PR diff를 분석하여
            어떤 문서를 업데이트해야 하는지 구체적으로 제안합니다.
            
            다음 문서 유형을 고려하세요:
            1. **README.md** — 새 기능, 설정 변경, 사용법 변경
            2. **API 문서** — 새 엔드포인트, 파라미터 변경, 응답 형식 변경
            3. **Javadoc** — 새 공개 클래스/메서드에 대한 문서화
            4. **아키텍처 문서** — 구조적 변경, 새 컴포넌트 추가
            
            각 제안에 대해:
            - 업데이트할 **파일/섹션**을 명시하세요
            - **추가/수정할 내용**을 구체적으로 작성하세요
            - **이유**를 설명하세요 (어떤 변경이 이 문서 업데이트를 필요로 하는지)
            
            컨텍스트에 코드 리뷰나 테스트 제안이 포함된 경우, 해당 인사이트를 반영하세요.
            
            반드시 document, README, API, Javadoc 등 문서 관련 키워드를 포함해서 작성하세요.""";

    private final ChatClient chatClient;

    public DocWriterAgent(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public String execute(String input, String context) {
        String userMessage = context.isBlank()
                ? "다음 PR diff에 대한 문서 업데이트를 제안해주세요:\n\n" + input
                : "다음 PR diff에 대한 문서 업데이트를 제안해주세요:\n\n" + input +
                  "\n\n이전 리뷰/테스트 결과 (이 인사이트를 문서에 반영):\n" + context;

        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userMessage)
                .call()
                .content();
    }
}

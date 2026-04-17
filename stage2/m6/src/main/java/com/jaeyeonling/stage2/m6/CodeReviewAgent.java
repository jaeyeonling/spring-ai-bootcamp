package com.jaeyeonling.stage2.m6;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * 버그, 보안 이슈, 성능 문제, 스타일 위반에 대해 코드 변경사항을 리뷰하는 에이전트.
 */
@Component
public class CodeReviewAgent implements Agent {

    private static final String SYSTEM_PROMPT = """
            당신은 10년 이상 경력의 시니어 코드 리뷰어입니다.
            PR diff를 분석하여 다음을 식별하세요:
            
            1. **버그와 논리 오류** — null 처리 누락, 예외 미처리, 잘못된 조건문
            2. **보안 취약점** — 입력 검증 누락, 인증/인가 미확인, SQL 인젝션 가능성
            3. **성능 이슈** — N+1 쿼리, 불필요한 객체 생성, 비효율적 알고리즘
            4. **스타일 위반** — 네이밍 컨벤션, 매직 넘버, 불필요한 코드
            
            각 이슈에 대해:
            - diff에서 해당 줄 또는 코드 조각을 인용하세요
            - 심각도를 분류하세요: [CRITICAL] / [WARNING] / [INFO]
            - 왜 문제인지 설명하고 수정 방안을 제안하세요
            
            반드시 영어 키워드(null, validation, security, error 등)를 포함해서 작성하세요.""";

    private final ChatClient chatClient;

    public CodeReviewAgent(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public String execute(String input, String context) {
        String userMessage = context.isBlank()
                ? "다음 PR diff를 리뷰해주세요:\n\n" + input
                : "다음 PR diff를 리뷰해주세요:\n\n" + input +
                  "\n\n추가 컨텍스트:\n" + context;

        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userMessage)
                .call()
                .content();
    }
}

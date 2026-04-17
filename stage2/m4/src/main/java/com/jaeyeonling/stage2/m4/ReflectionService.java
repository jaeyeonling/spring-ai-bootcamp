package com.jaeyeonling.stage2.m4;

/**
 * Reflection 패턴을 구현합니다: 생성 -> 검증 -> 재시도.
 *
 * LLM이 답변을 생성한 후(툴을 사용할 수도 있음), 이 서비스는
 * LLM에게 답변이 실제로 사용자의 질문에 답하는지 검증하도록 요청합니다.
 * 검증에 실패하면 새로운 시도로 재시도합니다.
 *
 * 이것이 잡는 것들:
 * - 잘못된 툴 호출 (주문에 관해 물었는데 FAQ 결과가 나온 경우)
 * - 잘못된 파라미터 (질문에서 잘못된 주문 ID가 추출된 경우)
 * - 잘못 해석된 결과 (툴이 데이터를 반환했지만 LLM이 잘못 요약한 경우)
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ReflectionService {

    private static final Logger log = LoggerFactory.getLogger(ReflectionService.class);

    private static final String VERIFICATION_PROMPT = """
            당신은 품질 검사자입니다. 다음 답변이
            사용자의 질문을 올바르고 완전하게 다루는지 평가하세요.

            규칙:
            - 답변이 구체적인 데이터(주문 상태, 매출 수치)를 사용하면 그것이 합리적인지 검증하세요
            - 답변이 "찾을 수 없음" 또는 "오류"라고 하면 FAIL로 처리하세요
            - 구체적인 답변이 예상될 때 모호한 답변이 나오면 FAIL입니다
            - 답변이 관련 정보로 질문을 올바르게 다루면 PASS입니다

            "PASS" 또는 "FAIL: <간단한 이유>"로만 응답하세요.""";

    private final ChatService chatService;
    private final ChatClient verifierClient;
    private final int maxRetries;

    public ReflectionService(
            ChatService chatService,
            ChatClient.Builder builder,
            @Value("${reflection.max-retries:2}") int maxRetries) {
        this.chatService = chatService;
        this.verifierClient = builder.build();
        this.maxRetries = maxRetries;
    }

    /**
     * Reflection을 사용한 채팅: 답변 생성, 검증, 필요하면 재시도.
     *
     * @param userMessage 사용자의 질문
     * @return 검증된 답변 (또는 최대 재시도 후 최선의 답변)
     */
    public String chatWithReflection(String userMessage) {
        String result = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            // 1단계: 생성 (ChatService가 툴 연결을 담당)
            result = chatService.chat(userMessage);

            // 2단계: 검증
            String verification = verifierClient.prompt()
                    .system(VERIFICATION_PROMPT)
                    .user("질문: %s\n답변: %s".formatted(userMessage, result))
                    .call()
                    .content();

            // 3단계: 확인
            if (verification != null && verification.trim().startsWith("PASS")) {
                log.info("{}번째 시도에서 답변 검증됨", attempt + 1);
                return result;
            }

            // 4단계: 로그 및 재시도
            log.warn("{}번째 시도에서 Reflection 실패: {}", attempt + 1, verification);
        }

        log.warn("질문에 대한 최대 재시도 소진: {}", userMessage);
        return result;
    }

    /**
     * 재시도 없이 단일 답변을 검증합니다.
     * 검증 단계를 독립적으로 테스트하는 데 유용합니다.
     *
     * @param question 원본 질문
     * @param answer   검증할 답변
     * @return 답변이 검증을 통과하면 true
     */
    public boolean verify(String question, String answer) {
        String verification = verifierClient.prompt()
                .system(VERIFICATION_PROMPT)
                .user("질문: %s\n답변: %s".formatted(question, answer))
                .call()
                .content();
        return verification != null && verification.trim().startsWith("PASS");
    }
}

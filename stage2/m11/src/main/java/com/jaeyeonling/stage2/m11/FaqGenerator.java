package com.jaeyeonling.stage2.m11;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * 감지된 반복 패턴을 기반으로 FAQ 후보를 생성합니다.
 *
 * LLM을 사용하여 상담 사례들을 분석하고 FAQ 형태의 답변을 작성합니다.
 */
@Service
public class FaqGenerator {

    private final ChatClient chatClient;

    public FaqGenerator(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * 반복 패턴에서 FAQ 후보를 생성합니다.
     *
     * TODO: 구현하세요
     * - 상담 사례들을 프롬프트에 포함하여 LLM에 FAQ 생성을 요청합니다.
     * - FAQ 형식: 질문 + 정확하고 간결한 답변
     * - 상담사 오답이나 예외 케이스는 제외합니다.
     *
     * @param pattern 감지된 반복 패턴
     * @return 생성된 FAQ 후보
     */
    public FaqCandidate generate(PatternDetector.RepeatedPattern pattern) {
        throw new UnsupportedOperationException("구현하세요");
    }

    /**
     * FAQ 후보 레코드.
     */
    public record FaqCandidate(
        String question,
        String answer,
        String intent,
        String content  // 벡터 DB에 저장할 전체 텍스트
    ) {}
}

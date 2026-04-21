package com.jaeyeonling.stage2.m11;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * 생성된 FAQ 후보가 기존 정책과 충돌하지 않는지 검증합니다.
 *
 * LLM을 사용하여 새로운 FAQ와 현행 정책 문서 간의 일관성을 확인합니다.
 */
@Service
public class PolicyValidator {

    private final ChatClient chatClient;

    public PolicyValidator(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * FAQ 후보를 기존 정책과 비교하여 검증합니다.
     *
     * TODO: 구현하세요
     * - 새로운 FAQ 후보와 기존 정책 문서를 LLM에 전달합니다.
     * - 일관성 여부(consistent), 충돌 사항(conflicts), 권고(recommendation)를 반환합니다.
     * - recommendation: "approve" | "reject" | "review"
     *
     * @param candidate 검증할 FAQ 후보
     * @param policyDocuments 현행 정책 문서 내용
     * @return 검증 결과
     */
    public ValidationResult validate(FaqGenerator.FaqCandidate candidate, String policyDocuments) {
        throw new UnsupportedOperationException("구현하세요");
    }

    /**
     * 정책 검증 결과.
     */
    public record ValidationResult(
        boolean consistent,
        List<String> conflicts,
        String recommendation  // "approve" | "reject" | "review"
    ) {}
}

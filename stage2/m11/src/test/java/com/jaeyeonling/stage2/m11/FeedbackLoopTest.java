package com.jaeyeonling.stage2.m11;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 데이터 피드백 루프 검증 테스트.
 *
 * 실행: ./gradlew :stage2:m11:test
 *
 * 사전 조건:
 *   - Qdrant가 실행 중이어야 합니다 (docker-compose up -d qdrant)
 *   - 환경변수에 OPENAI_API_KEY가 설정되어 있어야 합니다
 */
@SpringBootTest
class FeedbackLoopTest {

    @Autowired
    private PatternDetector patternDetector;

    @Autowired
    private FeedbackLoopService feedbackLoopService;

    @Test
    @DisplayName("반복 패턴 감지 — 동일 intent 3회 이상 질문이 감지된다")
    void patternDetectionFindsRepeatedQuestions() {
        // Arrange: 같은 intent의 질문을 3개 이상 준비
        LocalDateTime now = LocalDateTime.now();
        List<PatternDetector.ChatLog> logs = List.of(
            new PatternDetector.ChatLog("1", "배송 지연 보상 받을 수 있나요?", "네, 가능합니다.", "shipping_delay_compensation", now.minusDays(1)),
            new PatternDetector.ChatLog("2", "배송이 늦어지면 보상이 되나요?", "보상 절차를 안내드립니다.", "shipping_delay_compensation", now.minusDays(2)),
            new PatternDetector.ChatLog("3", "배송 지연 시 보상 정책이 있나요?", "14일 이상 지연 시 보상됩니다.", "shipping_delay_compensation", now.minusDays(3)),
            new PatternDetector.ChatLog("4", "반품 신청하려고요", "반품 절차를 안내합니다.", "return_request", now.minusDays(1)),
            new PatternDetector.ChatLog("5", "반품하고 싶어요", "접수 완료되었습니다.", "return_request", now.minusDays(2)),
            new PatternDetector.ChatLog("6", "반품 어떻게 하나요?", "반품 방법을 안내합니다.", "return_request", now.minusDays(4))
        );

        // Act
        List<PatternDetector.RepeatedPattern> patterns = patternDetector.detect(logs);

        // Assert: 2개의 반복 패턴이 감지되어야 함
        assertThat(patterns).hasSize(2);
        assertThat(patterns).allSatisfy(pattern -> {
            assertThat(pattern.occurrenceCount()).isGreaterThanOrEqualTo(3);
        });
    }

    @Test
    @DisplayName("패턴 감지 — 3회 미만 질문은 감지되지 않는다")
    void patternDetectionIgnoresInfrequentQuestions() {
        LocalDateTime now = LocalDateTime.now();
        List<PatternDetector.ChatLog> logs = List.of(
            new PatternDetector.ChatLog("1", "특이한 질문입니다", "답변입니다.", "rare_question", now.minusDays(1)),
            new PatternDetector.ChatLog("2", "비슷한 특이한 질문", "답변입니다.", "rare_question", now.minusDays(3))
        );

        List<PatternDetector.RepeatedPattern> patterns = patternDetector.detect(logs);

        assertThat(patterns).isEmpty();
    }

    @Test
    @DisplayName("FAQ 생성 — 반복 패턴에서 FAQ 후보가 생성된다")
    void generatedFaqHasRequiredStructure() {
        LocalDateTime now = LocalDateTime.now();
        PatternDetector.RepeatedPattern pattern = new PatternDetector.RepeatedPattern(
            "shipping_delay_compensation",
            List.of(
                new PatternDetector.ChatLog("1", "배송 지연 보상 받을 수 있나요?", "네, 14일 이상 지연 시 보상됩니다.", "shipping_delay_compensation", now.minusDays(1)),
                new PatternDetector.ChatLog("2", "배송이 늦어지면 보상이 되나요?", "보상 절차를 안내드립니다.", "shipping_delay_compensation", now.minusDays(2)),
                new PatternDetector.ChatLog("3", "배송 지연 시 보상 정책이 있나요?", "14일 이상 지연 시 보상됩니다.", "shipping_delay_compensation", now.minusDays(3))
            ),
            3
        );

        FaqGenerator.FaqCandidate faq = feedbackLoopService.generateFaq(pattern);

        assertThat(faq).isNotNull();
        assertThat(faq.question()).isNotBlank();
        assertThat(faq.answer()).isNotBlank();
        assertThat(faq.intent()).isEqualTo("shipping_delay_compensation");
        assertThat(faq.content()).isNotBlank();
    }

    @Test
    @DisplayName("정책 검증 — 정책과 모순되는 FAQ는 reject된다")
    void policyValidationCatchesContradictions() {
        FaqGenerator.FaqCandidate contradictingFaq = new FaqGenerator.FaqCandidate(
            "반품은 언제든 가능한가요?",
            "네, 구매 후 언제든지 반품이 가능합니다.",
            "return_policy",
            "### 반품은 언제든 가능한가요?\n네, 구매 후 언제든지 반품이 가능합니다."
        );

        String policyDocuments = """
            반품 정책: 구매 후 30일 이내에만 반품이 가능합니다.
            상품은 미사용, 원본 포장 상태여야 합니다.
            30일이 경과한 상품은 반품이 불가합니다.
            """;

        PolicyValidator.ValidationResult result = feedbackLoopService.validatePolicy(
            contradictingFaq, policyDocuments);

        assertThat(result.consistent()).isFalse();
        assertThat(result.recommendation()).isIn("reject", "review");
    }
}

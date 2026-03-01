package com.jaeyeonling.week02;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.chat.evaluation.RelevancyEvaluator;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 테스트 질문 세트를 대상으로 챗봇을 자동 평가합니다.
 *
 * test_questions.json에서 질문을 로드하고, 각 질문을 챗봇에 전송하고,
 * Spring AI의 RelevancyEvaluator를 사용해 관련성을 평가하고,
 * 요약 보고서를 출력합니다.
 *
 * 이것이 AI 엔지니어링을 체계적으로 만드는 피드백 루프입니다:
 *   [변경 적용] → [평가 실행] → [실패 내용 읽기] → [변경 적용] → ...
 */
@Component
public class EvaluationRunner {

    private static final Logger log = LoggerFactory.getLogger(EvaluationRunner.class);

    private final ChatService chatService;
    private final RelevancyEvaluator evaluator;
    private final ObjectMapper objectMapper;

    public EvaluationRunner(ChatService chatService, ChatClient.Builder chatClientBuilder) {
        this.chatService = chatService;
        this.objectMapper = new ObjectMapper();

        // RelevancyEvaluator는 LLM을 통해 답변 품질을 판단하기 위해 ChatClient를 사용합니다.
        // 질문, 예상 답변, 실제 답변을 LLM에 보내 관련성을 점수화합니다.
        this.evaluator = new RelevancyEvaluator(chatClientBuilder);
    }

    /**
     * JSON 파일에서 테스트 질문을 로드합니다.
     *
     * @param filePath test_questions.json 경로
     * @return 예상 답변이 포함된 테스트 질문 목록
     */
    public List<TestQuestion> loadTestQuestions(String filePath) throws IOException {
        return objectMapper.readValue(new File(filePath), new TypeReference<>() {});
    }

    /**
     * 모든 테스트 질문에 대해 평가를 실행하고 보고서를 출력합니다.
     *
     * @param testQuestions 평가할 질문들
     */
    public EvaluationReport runEvaluation(List<TestQuestion> testQuestions) {
        int passed = 0;
        int failed = 0;
        List<FailedQuestion> failures = new ArrayList<>();

        for (int i = 0; i < testQuestions.size(); i++) {
            TestQuestion tq = testQuestions.get(i);
            log.info("[평가 {}/{}] Q: {}", i + 1, testQuestions.size(), tq.question());

            try {
                // 1. 챗봇 답변 가져오기
                String actualAnswer = chatService.answerQuestion(tq.question());
                log.info("[평가 {}/{}] A: {}", i + 1, testQuestions.size(),
                        actualAnswer.substring(0, Math.min(100, actualAnswer.length())));

                // 2. EvaluationRequest 구성
                // - 기대 답변을 Document로 래핑 (평가기의 컨텍스트 역할)
                // - 실제 답변과 질문을 함께 전달
                EvaluationRequest request = new EvaluationRequest(
                        tq.question(),
                        List.of(new Document(tq.expectedAnswer())),
                        actualAnswer
                );

                // 3. 관련성 평가
                EvaluationResponse response = evaluator.evaluate(request);
                boolean isPass = response.isPass();

                if (isPass) {
                    passed++;
                    log.info("[평가 {}/{}] → PASS", i + 1, testQuestions.size());
                } else {
                    failed++;
                    String feedback = response.getScore() != 0
                            ? "Score: " + response.getScore()
                            : "Not relevant";
                    failures.add(new FailedQuestion(
                            tq.question(), tq.expectedAnswer(), actualAnswer, feedback));
                    log.warn("[평가 {}/{}] → FAIL | Expected: {}...", i + 1, testQuestions.size(),
                            tq.expectedAnswer().substring(0, Math.min(80, tq.expectedAnswer().length())));
                }
            } catch (Exception e) {
                failed++;
                failures.add(new FailedQuestion(
                        tq.question(), tq.expectedAnswer(), "ERROR: " + e.getMessage(), e.getClass().getSimpleName()));
                log.error("[평가 {}/{}] → ERROR: {}", i + 1, testQuestions.size(), e.getMessage());
            }
        }

        int total = testQuestions.size();
        double accuracy = total > 0 ? (double) passed / total * 100.0 : 0.0;

        return new EvaluationReport(total, passed, failed, accuracy, failures);
    }

    /**
     * 형식화된 평가 보고서를 콘솔에 출력합니다.
     */
    public void printReport(EvaluationReport report) {
        System.out.println();
        System.out.println("=== 평가 보고서 ===");
        System.out.printf("총계: %d | 통과: %d | 실패: %d | 정확도: %.1f%%%n",
                report.total(), report.passed(), report.failed(), report.accuracy());

        if (!report.failures().isEmpty()) {
            System.out.println();
            System.out.println("실패 목록:");
            for (FailedQuestion fq : report.failures()) {
                System.out.println("  ─────────────────────────────────");
                System.out.printf("  Q: %s%n", fq.question());
                System.out.printf("  Expected: %s%n", fq.expectedAnswer());
                System.out.printf("  Actual:   %s%n", fq.actualAnswer());
                System.out.printf("  Feedback: %s%n", fq.feedback());
            }
        }
        System.out.println();
    }

    /**
     * 예상 답변이 포함된 단일 테스트 질문.
     */
    public record TestQuestion(
            String question,
            @JsonProperty("expected_answer") String expectedAnswer
    ) {}

    /**
     * 평가 실행의 요약.
     */
    public record EvaluationReport(
            int total,
            int passed,
            int failed,
            double accuracy,
            List<FailedQuestion> failures
    ) {}

    /**
     * 단일 평가 실패의 세부 정보.
     */
    public record FailedQuestion(
            String question,
            String expectedAnswer,
            String actualAnswer,
            String feedback
    ) {}
}

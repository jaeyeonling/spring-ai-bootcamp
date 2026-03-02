package com.jaeyeonling.week02;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 기준선 평가 테스트.
 *
 * 30개 테스트 질문으로 현재 챗봇의 정확도를 측정합니다.
 * 실제 OpenAI API를 호출하므로 OPENAI_API_KEY가 설정되어 있어야 합니다.
 *
 * 실행: ./gradlew :week02:test --tests "*.EvaluationRunnerTest"
 */
@SpringBootTest
class EvaluationRunnerTest {

    @Autowired
    private EvaluationRunner evaluationRunner;

    @Value("${evaluation.test-questions-path}")
    private String testQuestionsPath;

    @Test
    @DisplayName("기준선 평가 — 50개 영어 질문의 정확도를 측정한다")
    void baselineEvaluation() throws Exception {
        List<EvaluationRunner.TestQuestion> questions = evaluationRunner.loadTestQuestions(testQuestionsPath);
        assertThat(questions).hasSize(50);

        EvaluationRunner.EvaluationReport report = evaluationRunner.runEvaluation(questions);
        evaluationRunner.printReport(report);

        // 기준선은 통과 기준 없이 현재 정확도만 기록
        assertThat(report.total()).isEqualTo(50);
        System.out.printf(">>> 기준선 정확도 (영어): %.1f%% (%d/%d)%n",
                report.accuracy(), report.passed(), report.total());
    }

    @Test
    @DisplayName("한글 기준선 평가 — 50개 한국어 질문의 정확도를 측정한다")
    void koreanBaselineEvaluation() throws Exception {
        String koPath = testQuestionsPath.replace("test_questions.json", "test_questions_ko.json");
        List<EvaluationRunner.TestQuestion> questions = evaluationRunner.loadTestQuestions(koPath);
        assertThat(questions).hasSize(50);

        EvaluationRunner.EvaluationReport report = evaluationRunner.runEvaluation(questions);
        evaluationRunner.printReport(report);

        assertThat(report.total()).isEqualTo(50);
        System.out.printf(">>> 기준선 정확도 (한글): %.1f%% (%d/%d)%n",
                report.accuracy(), report.passed(), report.total());
    }
}

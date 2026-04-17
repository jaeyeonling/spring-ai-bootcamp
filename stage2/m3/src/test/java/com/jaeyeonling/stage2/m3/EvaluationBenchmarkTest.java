package com.jaeyeonling.stage2.m3;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.util.List;

/**
 * Week 05 관측성 파이프라인 벤치마크.
 *
 * test_questions.json 전체를 배치 평가하고
 * 관련성 점수, 환각 탐지 수, 검색 지연시간을 측정한다.
 */
@SpringBootTest
class EvaluationBenchmarkTest {

    @Autowired
    EvaluationService evaluationService;

    @Value("${data.test-questions-path}")
    String testQuestionsPath;

    @Test
    @DisplayName("[벤치마크] 전체 테스트셋 배치 평가 — 관련성, 환각, 지연시간 측정")
    void fullBatchEvaluation() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<TestQuestion> questions = mapper.readValue(
            new File(testQuestionsPath),
            new TypeReference<>() {}
        );

        List<EvaluationService.TestCase> testCases = questions.stream()
            .map(q -> new EvaluationService.TestCase(q.question(), q.expectedAnswer()))
            .toList();

        long start = System.currentTimeMillis();
        List<EvaluationService.EvaluationResult> results =
            evaluationService.runBatchEvaluation(testCases);
        long totalMs = System.currentTimeMillis() - start;

        // 집계
        long relevantCount = results.stream().filter(EvaluationService.EvaluationResult::isRelevant).count();
        long hallucinationCount = results.stream().filter(EvaluationService.EvaluationResult::isHallucination).count();
        double avgRelevancy = results.stream()
            .mapToDouble(EvaluationService.EvaluationResult::relevancyScore)
            .average()
            .orElse(0.0);
        long avgLatencyMs = totalMs / results.size();

        System.out.println();
        System.out.printf("=== Week 05 배치 평가 결과 ===%n");
        System.out.printf("총 질문 수    : %d%n", results.size());
        System.out.printf("관련성 통과   : %d/%d (%.1f%%)%n",
            relevantCount, results.size(), 100.0 * relevantCount / results.size());
        System.out.printf("평균 관련성   : %.2f%n", avgRelevancy);
        System.out.printf("환각 탐지     : %d건%n", hallucinationCount);
        System.out.printf("총 소요시간   : %d ms%n", totalMs);
        System.out.printf("질문당 평균   : %d ms%n", avgLatencyMs);
        System.out.println();
    }

    @Test
    @DisplayName("[벤치마크] 인시던트 진단 — 검색 실패 vs 생성 실패 판별")
    void incidentDiagnosisBenchmark() {
        record IncidentCase(String question, String badAnswer, String description) {}

        List<IncidentCase> cases = List.of(
            new IncidentCase(
                "전자제품 배송 기간이 어떻게 되나요?",
                "모든 주문에 무료 배송을 제공합니다",
                "배송 정책 오답"
            ),
            new IncidentCase(
                "맞춤 제작 상품을 반품할 수 있나요?",
                "30일 이내에 반품할 수 있습니다",
                "맞춤 제작 반품 오답"
            )
        );

        System.out.println();
        System.out.printf("=== Week 05 인시던트 진단 결과 ===%n");

        for (IncidentCase c : cases) {
            EvaluationService.IncidentDiagnosis diagnosis =
                evaluationService.diagnoseIncident(c.question(), c.badAnswer());

            System.out.printf("%n[%s]%n", c.description());
            System.out.printf("  질문       : %s%n", c.question());
            System.out.printf("  잘못된 답변: %s%n", c.badAnswer());
            System.out.printf("  근본 원인  : %s%n", diagnosis.rootCause());
            System.out.printf("  근거       : %s%n", diagnosis.evidence());
            System.out.printf("  수정 방안  : %s%n", diagnosis.suggestedFix());
            System.out.printf("  관련성 점수: %.2f%n", diagnosis.relevancyScore());
            System.out.printf("  환각 여부  : %s%n", diagnosis.isHallucination() ? "YES" : "NO");
        }
        System.out.println();
    }

    public record TestQuestion(
        String question,
        @JsonProperty("expected_answer") String expectedAnswer
    ) {}
}

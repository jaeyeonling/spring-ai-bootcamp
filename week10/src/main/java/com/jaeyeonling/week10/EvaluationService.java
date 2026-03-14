package com.jaeyeonling.week10;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.chat.evaluation.FactCheckingEvaluator;
import org.springframework.ai.chat.evaluation.RelevancyEvaluator;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 환각 탐지 및 답변 품질 점수화를 위한 배치 평가 서비스.
 * Week 05의 EvaluationService를 Week 10 패키지로 이식합니다.
 */
@Service
public class EvaluationService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationService.class);

    private final ChatClient chatClient;
    private final RelevancyEvaluator relevancyEvaluator;
    private final FactCheckingEvaluator factCheckingEvaluator;
    private final MetricsService metricsService;
    private final ObservationRegistry observationRegistry;
    private final VectorStore vectorStore;

    public EvaluationService(
            ChatClient.Builder chatClientBuilder,
            MetricsService metricsService,
            ObservationRegistry observationRegistry,
            VectorStore vectorStore) {
        this.chatClient = chatClientBuilder.build();
        this.relevancyEvaluator = new RelevancyEvaluator(chatClientBuilder);
        this.factCheckingEvaluator = FactCheckingEvaluator.builder(chatClientBuilder).build();
        this.metricsService = metricsService;
        this.observationRegistry = observationRegistry;
        this.vectorStore = vectorStore;
    }

    /**
     * 테스트 질문 세트에 대한 배치 평가를 실행합니다.
     *
     * @param testCases (질문, 예상 답변) 쌍 목록
     * @return 각 테스트 케이스에 대한 평가 결과
     */
    public List<EvaluationResult> runBatchEvaluation(List<TestCase> testCases) {
        return Observation.createNotStarted("rag.batch-evaluation", observationRegistry)
            .lowCardinalityKeyValue("batch.size", String.valueOf(testCases.size()))
            .observe(() -> {
                List<EvaluationResult> results = new ArrayList<>();

                for (TestCase testCase : testCases) {
                    EvaluationResult result = evaluateSingle(testCase);
                    results.add(result);
                }

                double avgRelevancy = results.stream()
                    .mapToDouble(EvaluationResult::relevancyScore)
                    .average()
                    .orElse(0.0);

                long hallucinationCount = results.stream()
                    .filter(EvaluationResult::isHallucination)
                    .count();

                metricsService.updateQualityScore(avgRelevancy);

                log.info("배치 평가 완료: {}개 질문, 평균 관련성: {}, 환각: {}",
                    testCases.size(), String.format("%.2f", avgRelevancy), hallucinationCount);

                return results;
            });
    }

    private EvaluationResult evaluateSingle(TestCase testCase) {
        // 1단계 — 검색
        long start = System.currentTimeMillis();
        List<Document> documents = vectorStore.similaritySearch(
            SearchRequest.builder().query(testCase.question()).topK(5).build()
        );
        metricsService.recordSearchLatency(System.currentTimeMillis() - start);
        metricsService.recordSearchResultCount(documents.size());

        // 2단계 — 답변 생성
        String context = documents.stream()
            .map(Document::getText)
            .collect(Collectors.joining("\n\n"));

        String answer = chatClient.prompt()
            .system("다음 컨텍스트에 기반해서만 답변하세요:\n" + context)
            .user(testCase.question())
            .call()
            .content();

        // 3단계 — 관련성 평가
        EvaluationRequest evalRequest = new EvaluationRequest(testCase.question(), documents, answer);
        EvaluationResponse relevancy = relevancyEvaluator.evaluate(evalRequest);
        double relevancyScore = relevancy.getScore();
        boolean isRelevant = relevancy.isPass();

        // 4단계 — 환각(팩트 체킹) 평가
        EvaluationResponse factCheck = factCheckingEvaluator.evaluate(evalRequest);
        boolean isHallucination = !factCheck.isPass();

        if (isHallucination) {
            metricsService.incrementHallucinationCount();
            log.warn("환각 감지됨 - 질문: '{}' — 답변: '{}'", testCase.question(), answer);
        }

        return new EvaluationResult(
            testCase.question(),
            answer,
            documents,
            relevancyScore,
            isRelevant,
            isHallucination
        );
    }

    // -----------------------------------------------------------------------
    // 레코드
    // -----------------------------------------------------------------------

    public record TestCase(String question, String expectedAnswer) {}

    public record EvaluationResult(
            String question,
            String answer,
            List<Document> documents,
            double relevancyScore,
            boolean isRelevant,
            boolean isHallucination) {
    }
}

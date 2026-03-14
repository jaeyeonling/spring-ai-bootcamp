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
 * 운영 챗봇 파이프라인의 품질을 평가하는 서비스.
 *
 * Week 05의 EvaluationService를 개선:
 * - 실제 ChatService(툴 호출 포함)를 사용해 평가 — 운영 파이프라인과 동일한 경로
 * - RelevancyEvaluator + FactCheckingEvaluator로 관련성/환각 이중 검증
 * - MetricsService에 평가 결과 기록
 *
 * 핵심 철학: 평가는 실제 운영 파이프라인을 그대로 평가해야 한다.
 * 별도 ChatClient를 만들면 툴 호출 로직이 빠진 "다른 시스템"을 평가하게 된다.
 */
@Service
public class EvaluationService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationService.class);

    private final ChatService chatService;              // 실제 운영 파이프라인 (툴 포함)
    private final RelevancyEvaluator relevancyEvaluator;
    private final FactCheckingEvaluator factCheckingEvaluator;
    private final MetricsService metricsService;
    private final ObservationRegistry observationRegistry;
    private final VectorStore vectorStore;

    public EvaluationService(
            ChatService chatService,
            ChatClient.Builder chatClientBuilder,
            MetricsService metricsService,
            ObservationRegistry observationRegistry,
            VectorStore vectorStore) {
        this.chatService = chatService;
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

    /**
     * 실제 운영 파이프라인(ChatService)을 통해 단일 테스트 케이스를 평가합니다.
     *
     * ChatService를 직접 호출하므로 툴 호출(getOrderStatus, searchFaq)이 포함된
     * 실제 사용자 경험을 평가합니다.
     */
    private EvaluationResult evaluateSingle(TestCase testCase) {
        // 1단계 — 실제 운영 파이프라인으로 답변 생성 (툴 호출 포함)
        long start = System.currentTimeMillis();
        ChatService.ChatResult chatResult = chatService.chat(testCase.question());
        long elapsed = System.currentTimeMillis() - start;
        log.debug("[평가] '{}' → {}ms, {} 토큰", testCase.question(), elapsed,
                chatResult.tokenUsage().totalTokens());

        String answer = chatResult.answer();

        // 2단계 — 평가를 위해 관련 문서 검색 (EvaluationRequest 구성용)
        List<Document> documents = vectorStore.similaritySearch(
            SearchRequest.builder().query(testCase.question()).topK(5).build()
        );
        metricsService.recordSearchResultCount(documents.size());

        // 3단계 — 관련성 평가
        EvaluationRequest evalRequest = new EvaluationRequest(testCase.question(), documents, answer);
        EvaluationResponse relevancy = relevancyEvaluator.evaluate(evalRequest);
        double relevancyScore = relevancy.getScore();
        boolean isRelevant = relevancy.isPass();

        // 4단계 — 환각(팩트 체킹) 평가
        // expectedAnswer가 있으면 검색 문서와 함께 레퍼런스로 포함하여 평가 정확도를 높인다.
        // "LLM이 생성한 답변이 레퍼런스(문서 + 기대 답변) 범위를 벗어나는지" 판단하는 것이 핵심.
        List<Document> factCheckDocs = new ArrayList<>(documents);
        if (testCase.expectedAnswer() != null && !testCase.expectedAnswer().isBlank()) {
            factCheckDocs.add(new Document("expected: " + testCase.expectedAnswer()));
        }
        EvaluationRequest factCheckRequest = new EvaluationRequest(
                testCase.question(), factCheckDocs, answer);
        EvaluationResponse factCheck = factCheckingEvaluator.evaluate(factCheckRequest);
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

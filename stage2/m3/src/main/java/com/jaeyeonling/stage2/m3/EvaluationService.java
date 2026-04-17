package com.jaeyeonling.stage2.m3;

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
 *
 * 테스트 질문을 RAG 파이프라인에 통과시키고, Spring AI 평가기로 각 답변을 평가하며,
 * 결과를 Micrometer 메트릭으로 내보냅니다.
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

                // 집계 메트릭 계산
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
     * 전체 파이프라인을 통해 단일 테스트 케이스를 평가합니다.
     */
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

        // 5단계 — 환각 감지 시 메트릭 기록
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

    /**
     * 인시던트 분석을 위한 단일 질문 평가.
     * 근본 원인 조사를 위한 자세한 진단을 제공합니다.
     */
    public IncidentDiagnosis diagnoseIncident(String question, String badAnswer) {
        // 1. 문서 검색
        List<Document> documents = vectorStore.similaritySearch(
            SearchRequest.builder().query(question).topK(5).build()
        );

        // 2. 검색 결과가 없거나 관련성이 없으면 → 검색 실패
        if (documents.isEmpty()) {
            return new IncidentDiagnosis(
                question, badAnswer,
                "retrieval",
                "검색 결과가 없음 — 벡터 저장소에 관련 문서가 없거나 임베딩 불일치",
                "청킹 전략 개선 또는 FAQ 데이터 재인제스트 필요",
                0.0, false
            );
        }

        // 3. 검색된 문서로 관련성 평가
        EvaluationRequest evalRequest = new EvaluationRequest(question, documents, badAnswer);
        EvaluationResponse relevancy = relevancyEvaluator.evaluate(evalRequest);
        EvaluationResponse factCheck = factCheckingEvaluator.evaluate(evalRequest);

        double relevancyScore = relevancy.getScore();
        boolean isHallucination = !factCheck.isPass();

        // 4. 관련성이 낮으면 → 검색 실패 (올바른 문서를 못 찾음)
        if (!relevancy.isPass()) {
            return new IncidentDiagnosis(
                question, badAnswer,
                "retrieval",
                "검색된 문서가 질문과 관련 없음 (관련성 점수: " + String.format("%.2f", relevancyScore) + ") — " + relevancy.getFeedback(),
                "ReRanker 또는 QueryTransform 전략 적용, 또는 청킹 단위 개선 필요",
                relevancyScore, isHallucination
            );
        }

        // 5. 문서는 있는데 답변이 틀리면 → 생성 실패 (LLM이 컨텍스트를 잘못 해석)
        return new IncidentDiagnosis(
            question, badAnswer,
            "generation",
            "관련 문서가 검색됐으나 답변이 사실과 불일치 — LLM이 컨텍스트를 무시하거나 잘못 해석. " + factCheck.getFeedback(),
            "시스템 프롬프트 강화 또는 temperature 낮추기 (현재: 0.1 → 0.0)",
            relevancyScore, isHallucination
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

    public record IncidentDiagnosis(
            String question,
            String badAnswer,
            String rootCause,         // "retrieval" 또는 "generation"
            String evidence,          // 진단 설명
            String suggestedFix,      // 수정 방법
            double relevancyScore,
            boolean isHallucination) {
    }
}

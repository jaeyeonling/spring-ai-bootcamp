package com.jaeyeonling.week05;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.chat.evaluation.RelevancyEvaluator;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

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
    private final MetricsService metricsService;
    private final ObservationRegistry observationRegistry;
    private final VectorStore vectorStore;

    // TODO: FactCheckingEvaluator를 추가하세요 (Spring AI 1.1.x 이상에서 사용 가능).
    //
    // 참고: Spring AI 버전에 따라 FactCheckingEvaluator 클래스명이 다를 수 있습니다.
    // 만약 클래스를 찾을 수 없다면 다음 대안을 사용하세요:
    //   - RelevancyEvaluator를 팩트 체킹 프롬프트와 함께 사용
    //   - ChatClient를 직접 호출하여 "이 답변이 제공된 컨텍스트의 사실과 일치하나요?"를 판단
    //
    // private final FactCheckingEvaluator factCheckingEvaluator;

    public EvaluationService(
            ChatClient.Builder chatClientBuilder,
            MetricsService metricsService,
            ObservationRegistry observationRegistry,
            VectorStore vectorStore) {
        this.chatClient = chatClientBuilder.build();
        this.relevancyEvaluator = new RelevancyEvaluator(chatClientBuilder);
        this.metricsService = metricsService;
        this.observationRegistry = observationRegistry;
        this.vectorStore = vectorStore;

        // TODO: FactCheckingEvaluator 초기화 (Spring AI 1.1.x 이상)
        // this.factCheckingEvaluator = new FactCheckingEvaluator(chatClientBuilder);
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

                // TODO: 집계 메트릭 계산
                //
                // double avgRelevancy = results.stream()
                //     .mapToDouble(EvaluationResult::relevancyScore)
                //     .average()
                //     .orElse(0.0);
                //
                // long hallucinationCount = results.stream()
                //     .filter(EvaluationResult::isHallucination)
                //     .count();
                //
                // metricsService.updateQualityScore(avgRelevancy);
                //
                // log.info("배치 평가 완료: {}개 질문, 평균 관련성: {}, 환각: {}",
                //     testCases.size(), String.format("%.2f", avgRelevancy), hallucinationCount);

                return results;
            });
    }

    /**
     * 전체 파이프라인을 통해 단일 테스트 케이스를 평가합니다.
     */
    private EvaluationResult evaluateSingle(TestCase testCase) {
        // TODO: 1단계 — 질문에 대한 문서를 검색하세요.
        //
        // 관련 문서를 가져오기 위해 벡터 저장소 또는 검색 전략을 사용하세요.
        // metricsService.recordSearchLatency()로 검색 지연시간을 기록하세요.
        //
        // long start = System.currentTimeMillis();
        // List<Document> documents = vectorStore.similaritySearch(
        //     SearchRequest.builder().query(testCase.question()).topK(5).build()
        // );
        // metricsService.recordSearchLatency(System.currentTimeMillis() - start);

        List<Document> documents = List.of(); // TODO: 실제 검색으로 교체

        // TODO: 2단계 — 검색된 문서를 사용해 답변을 생성하세요.
        //
        // String context = documents.stream()
        //     .map(Document::getText)
        //     .collect(Collectors.joining("\n\n"));
        //
        // String answer = chatClient.prompt()
        //     .system("컨텍스트에 기반해 답변하세요: " + context)
        //     .user(testCase.question())
        //     .call()
        //     .content();

        String answer = ""; // TODO: 실제 생성으로 교체

        // TODO: 3단계 — 관련성에 대해 답변을 평가하세요.
        //
        // EvaluationRequest evalRequest = new EvaluationRequest(
        //     testCase.question(), documents, answer);
        // EvaluationResponse relevancy = relevancyEvaluator.evaluate(evalRequest);

        double relevancyScore = 0.0; // TODO: 실제 점수로 교체
        boolean isRelevant = false;  // TODO: 실제 결과로 교체

        // TODO: 4단계 — 환각(팩트 체킹)에 대해 평가하세요.
        //
        // EvaluationResponse factCheck = factCheckingEvaluator.evaluate(evalRequest);
        // boolean isHallucination = !factCheck.isPass();

        boolean isHallucination = false; // TODO: 실제 결과로 교체

        // TODO: 5단계 — 메트릭을 기록하고 문제를 플래그하세요.
        //
        // if (isHallucination) {
        //     metricsService.incrementHallucinationCount();
        //     log.warn("환각 감지됨 - 질문: '{}' — 답변: '{}'",
        //         testCase.question(), answer);
        // }

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
        // TODO: 인시던트 진단을 구현하세요.
        //
        // 1. 질문에 대한 문서 검색
        // 2. 검색된 문서에 올바른 정보가 포함되어 있는지 확인
        // 3. 없다면 → 검색 실패
        // 4. 있다면 → 생성 실패 (LLM이 컨텍스트를 무시하거나 잘못 해석)
        // 5. 두 평가기 모두로 답변 점수화
        // 6. 구조화된 진단 반환

        throw new UnsupportedOperationException("인시던트 진단을 구현하세요");
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

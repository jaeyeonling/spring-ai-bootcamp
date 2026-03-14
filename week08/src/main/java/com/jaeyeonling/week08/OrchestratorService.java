package com.jaeyeonling.week08;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * PR 리뷰 작업을 분해하고, 워커 에이전트에 배분하고,
 * 결합된 결과를 합성하는 중앙 오케스트레이터.
 *
 * 여러 워크플로우 패턴을 지원합니다:
 * - "chain": 각 에이전트의 출력이 다음 에이전트로 전달되는 순차 실행
 * - "orchestrator": 작업 분해와 합성을 포함한 병렬 실행
 */
@Service
public class OrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorService.class);

    private static final String DECOMPOSE_PROMPT = """
            당신은 PR 리뷰 오케스트레이터입니다. 다음 PR diff를 분석하고,
            세 전문가 에이전트 각각이 집중해야 할 영역을 계획하세요:
            
            1. **코드 리뷰어** — 이 diff에서 특히 주의 깊게 봐야 할 부분은?
            2. **테스트 엔지니어** — 어떤 시나리오의 테스트가 가장 중요한가?
            3. **문서 작성자** — 어떤 문서가 업데이트되어야 하는가?
            
            각 에이전트에게 전달할 구체적인 지시사항을 작성하세요.""";

    private static final String SYNTHESIZE_PROMPT = """
            당신은 PR 리뷰 합성기입니다. 세 전문가의 분석 결과를 하나의 일관된 리포트로 합성하세요.
            
            규칙:
            - 중복을 제거하세요
            - 이슈를 심각도순으로 정렬하세요 (critical > warning > info)
            - 가장 중요한 발견사항을 최상단 요약에 배치하세요
            - 코드 리뷰, 테스트 제안, 문서 업데이트 섹션을 명확하게 구분하세요""";

    private final ChatClient chatClient;
    private final CodeReviewAgent codeReviewAgent;
    private final TestWriterAgent testWriterAgent;
    private final DocWriterAgent docWriterAgent;

    @Value("${workflow.pattern:orchestrator}")
    private String workflowPattern;

    @Value("${workflow.timeout-seconds:60}")
    private int timeoutSeconds;

    public OrchestratorService(ChatClient.Builder builder,
                               CodeReviewAgent codeReviewAgent,
                               TestWriterAgent testWriterAgent,
                               DocWriterAgent docWriterAgent) {
        this.chatClient = builder.build();
        this.codeReviewAgent = codeReviewAgent;
        this.testWriterAgent = testWriterAgent;
        this.docWriterAgent = docWriterAgent;
    }

    /**
     * 설정된 워크플로우 패턴으로 PR 리뷰를 실행합니다.
     *
     * @param prDiff 리뷰할 PR diff 내용
     * @return 결합된 리뷰 리포트
     */
    public String review(String prDiff) {
        return switch (workflowPattern) {
            case "chain" -> runChainWorkflow(prDiff);
            case "orchestrator" -> runOrchestratorWorkflow(prDiff);
            default -> throw new IllegalArgumentException("알 수 없는 워크플로우 패턴: " + workflowPattern);
        };
    }

    // ── 체인 워크플로우 ──────────────────────────────────────────────────

    private String runChainWorkflow(String prDiff) {
        log.info("[Chain] 1단계: CodeReviewAgent 실행");
        String review = codeReviewAgent.execute(prDiff, "");

        log.info("[Chain] 2단계: TestWriterAgent 실행 (코드 리뷰 결과를 컨텍스트로)");
        String tests = testWriterAgent.execute(prDiff, review);

        log.info("[Chain] 3단계: DocWriterAgent 실행 (리뷰+테스트를 컨텍스트로)");
        String docs = docWriterAgent.execute(prDiff, review + "\n\n" + tests);

        return formatReport(review, tests, docs);
    }

    // ── 오케스트레이터-워커 워크플로우 ────────────────────────────────────

    private String runOrchestratorWorkflow(String prDiff) {
        // 1단계: 분해 — LLM이 각 에이전트의 집중 영역을 계획
        log.info("[Orchestrator] 1단계: 작업 분해");
        String plan = chatClient.prompt()
                .system(DECOMPOSE_PROMPT)
                .user(prDiff)
                .call()
                .content();

        // 2단계: 배분 — 3개 에이전트를 병렬 실행
        log.info("[Orchestrator] 2단계: 에이전트 병렬 실행");
        try {
            CompletableFuture<String> reviewFuture = CompletableFuture.supplyAsync(() ->
                    codeReviewAgent.execute(prDiff, plan));
            CompletableFuture<String> testFuture = CompletableFuture.supplyAsync(() ->
                    testWriterAgent.execute(prDiff, plan));
            CompletableFuture<String> docFuture = CompletableFuture.supplyAsync(() ->
                    docWriterAgent.execute(prDiff, plan));

            String review = reviewFuture.get(timeoutSeconds, TimeUnit.SECONDS);
            String tests = testFuture.get(timeoutSeconds, TimeUnit.SECONDS);
            String docs = docFuture.get(timeoutSeconds, TimeUnit.SECONDS);

            // 3단계: 합성 — LLM이 세 출력을 하나의 리포트로 합성
            log.info("[Orchestrator] 3단계: 결과 합성");
            String combined = "## 코드 리뷰 결과\n" + review +
                    "\n\n## 테스트 제안\n" + tests +
                    "\n\n## 문서 업데이트 제안\n" + docs;

            return chatClient.prompt()
                    .system(SYNTHESIZE_PROMPT)
                    .user(combined)
                    .call()
                    .content();

        } catch (Exception e) {
            log.error("[Orchestrator] 에이전트 실행 실패", e);
            throw new RuntimeException("오케스트레이터 워크플로우 실패: " + e.getMessage(), e);
        }
    }

    // ── 리포트 형식화 ──────────────────────────────────────────────

    /**
     * 에이전트 출력을 구조화된 리포트로 형식화합니다.
     */
    private String formatReport(String review, String tests, String docs) {
        return """
                # PR 리뷰 리포트
                
                ## 코드 리뷰
                %s
                
                ## 제안 테스트
                %s
                
                ## 문서 업데이트
                %s
                """.formatted(review, tests, docs);
    }
}

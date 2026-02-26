package com.jaeyeonling.week08;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
        // TODO: 체인 워크플로우를 구현하세요.
        //  1단계: PR diff로 CodeReviewAgent를 실행합니다 (이전 컨텍스트 없음).
        //  2단계: PR diff와 코드 리뷰 출력을 컨텍스트로 TestWriterAgent를 실행합니다.
        //  3단계: PR diff와 이전 두 출력을 컨텍스트로 DocWriterAgent를 실행합니다.
        //  세 출력을 형식화된 리포트로 결합합니다.
        //
        //  예시:
        //    String review = codeReviewAgent.execute(prDiff, "");
        //    String tests = testWriterAgent.execute(prDiff, review);
        //    String docs = docWriterAgent.execute(prDiff, review + "\n\n" + tests);
        //    return formatReport(review, tests, docs);

        throw new UnsupportedOperationException("체인 워크플로우를 구현하세요");
    }

    // ── 오케스트레이터-워커 워크플로우 ────────────────────────────────────

    private String runOrchestratorWorkflow(String prDiff) {
        // TODO: 오케스트레이터-워커 워크플로우를 3단계로 구현하세요:
        //
        //  1단계 — 분해: ChatClient를 사용해 PR diff를 분석하고 계획을 수립합니다.
        //    LLM에게 각 에이전트의 구체적인 집중 영역을 파악하도록 요청합니다.
        //    예시: chatClient.prompt().system("...").user(prDiff).call().content()

        // TODO: 2단계 — 배분: CompletableFuture를 사용해 모든 에이전트를 병렬로 실행합니다.
        //    각 에이전트는 PR diff를 입력으로, 분해 계획을 컨텍스트로 받습니다.
        //    모든 에이전트가 완료될 때까지 기다립니다 (타임아웃 포함).
        //
        //    예시:
        //      CompletableFuture<String> reviewFuture = CompletableFuture.supplyAsync(() ->
        //              codeReviewAgent.execute(prDiff, plan));
        //      CompletableFuture<String> testFuture = CompletableFuture.supplyAsync(() ->
        //              testWriterAgent.execute(prDiff, plan));
        //      CompletableFuture<String> docFuture = CompletableFuture.supplyAsync(() ->
        //              docWriterAgent.execute(prDiff, plan));
        //
        //      String review = reviewFuture.get(timeoutSeconds, TimeUnit.SECONDS);
        //      String tests = testFuture.get(timeoutSeconds, TimeUnit.SECONDS);
        //      String docs = docFuture.get(timeoutSeconds, TimeUnit.SECONDS);

        // TODO: 3단계 — 합성: ChatClient를 사용해 세 에이전트의 출력을
        //    하나의 일관된 리포트로 결합합니다. 합성 프롬프트는 중복을 제거하고,
        //    이슈를 심각도순으로 우선순위를 매기고, 출력을 명확하게 형식화해야 합니다.

        throw new UnsupportedOperationException("오케스트레이터-워커 워크플로우를 구현하세요");
    }

    // ── 리포트 형식화 ──────────────────────────────────────────────

    /**
     * 에이전트 출력을 구조화된 리포트로 형식화합니다.
     */
    private String formatReport(String review, String tests, String docs) {
        // TODO: 세 에이전트의 출력을 읽기 쉬운 마크다운 리포트로 결합합니다.
        //  코드 리뷰, 제안 테스트, 문서 업데이트 섹션을 포함합니다.
        //  최상단에 가장 중요한 발견사항을 담은 요약 섹션을 추가합니다.
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

package com.jaeyeonling.stage2.m7;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 복합 FAQ 질문을 분해하고 전문 에이전트에 배분하여 합성하는 오케스트레이터.
 *
 * Week 08의 OrchestratorService 패턴을 FAQ 챗봇 도메인으로 변환.
 *
 * 지원하는 워크플로우 패턴:
 * - "chain": OrderAgent → PolicyAgent → RecommendAgent 순차 실행
 *            (각 에이전트 출력이 다음 에이전트의 컨텍스트로 전달)
 * - "orchestrator": LLM이 질문을 분해 → 3개 에이전트 병렬 실행 → LLM이 합성
 *
 * 단순 질문은 ChatService.chat()으로 처리하고,
 * 복합 질문(주문+정책, 배송+추천 등)은 이 서비스로 처리합니다.
 */
@Service
public class FaqOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(FaqOrchestratorService.class);

    private static final String DECOMPOSE_PROMPT = """
            당신은 FAQ 오케스트레이터입니다. 다음 고객 질문을 분석하고,
            세 전문 에이전트 각각이 집중해야 할 영역을 계획하세요:
            
            1. **주문/배송 에이전트** — 이 질문에서 주문, 배송, 환불 관련 부분은?
            2. **정책 에이전트** — 이 질문에서 회사 정책, 이용약관 관련 부분은?
            3. **추천 에이전트** — 이 질문에서 대안 제시나 다음 단계 안내가 필요한 부분은?
            
            각 에이전트에게 전달할 구체적인 지시사항을 작성하세요.""";

    private static final String SYNTHESIZE_PROMPT = """
            당신은 FAQ 응답 합성기입니다. 세 전문 에이전트의 답변을 하나의 일관된 응답으로 합성하세요.
            
            규칙:
            - 중복 내용을 제거하고 자연스럽게 연결하세요
            - 고객 관점에서 가장 중요한 정보를 먼저 배치하세요
            - 주문/배송 정보, 정책 설명, 추천 사항 순으로 구성하세요
            - 명확하고 친절한 어조로 작성하세요""";

    private final ChatClient chatClient;
    private final OrderAgent orderAgent;
    private final PolicyAgent policyAgent;
    private final RecommendAgent recommendAgent;

    @Value("${workflow.pattern:orchestrator}")
    private String workflowPattern;

    @Value("${workflow.timeout-seconds:60}")
    private int timeoutSeconds;

    public FaqOrchestratorService(
            ChatClient.Builder builder,
            OrderAgent orderAgent,
            PolicyAgent policyAgent,
            RecommendAgent recommendAgent) {
        this.chatClient = builder.build();
        this.orderAgent = orderAgent;
        this.policyAgent = policyAgent;
        this.recommendAgent = recommendAgent;
    }

    /**
     * 복합 FAQ 질문을 멀티 에이전트 워크플로우로 처리합니다.
     *
     * @param question 사용자 질문
     * @param faqContext FAQ 검색 결과 (FaqSearchTool에서 가져온 관련 문서)
     * @return 합성된 최종 답변
     */
    public String answer(String question, String faqContext) {
        return switch (workflowPattern) {
            case "chain" -> runChainWorkflow(question, faqContext);
            case "orchestrator" -> runOrchestratorWorkflow(question, faqContext);
            default -> throw new IllegalArgumentException("알 수 없는 워크플로우 패턴: " + workflowPattern);
        };
    }

    // ── 체인 워크플로우 ──────────────────────────────────────────────────

    private String runChainWorkflow(String question, String faqContext) {
        log.info("[Chain] 1단계: OrderAgent 실행");
        String orderAnswer = orderAgent.execute(question, faqContext);

        log.info("[Chain] 2단계: PolicyAgent 실행 (주문 답변을 컨텍스트로)");
        String policyAnswer = policyAgent.execute(question, faqContext + "\n\n주문/배송 답변:\n" + orderAnswer);

        log.info("[Chain] 3단계: RecommendAgent 실행 (주문+정책 답변을 컨텍스트로)");
        String recommendAnswer = recommendAgent.execute(
                question, faqContext + "\n\n주문/배송:\n" + orderAnswer + "\n\n정책:\n" + policyAnswer);

        return synthesize(orderAnswer, policyAnswer, recommendAnswer);
    }

    // ── 오케스트레이터-워커 워크플로우 ────────────────────────────────────

    private String runOrchestratorWorkflow(String question, String faqContext) {
        // 1단계: 분해 — LLM이 각 에이전트의 집중 영역을 계획
        log.info("[Orchestrator] 1단계: 질문 분해 (question={})", question);
        String plan = chatClient.prompt()
                .system(DECOMPOSE_PROMPT)
                .user(question)
                .call()
                .content();

        String contextWithPlan = faqContext + "\n\n오케스트레이터 계획:\n" + plan;

        // 2단계: 배분 — 3개 에이전트를 병렬 실행 (Week 08 CompletableFuture 패턴)
        log.info("[Orchestrator] 2단계: 에이전트 병렬 실행");
        try {
            CompletableFuture<String> orderFuture = CompletableFuture.supplyAsync(() ->
                    orderAgent.execute(question, contextWithPlan));
            CompletableFuture<String> policyFuture = CompletableFuture.supplyAsync(() ->
                    policyAgent.execute(question, contextWithPlan));
            CompletableFuture<String> recommendFuture = CompletableFuture.supplyAsync(() ->
                    recommendAgent.execute(question, contextWithPlan));

            String orderAnswer = orderFuture.get(timeoutSeconds, TimeUnit.SECONDS);
            String policyAnswer = policyFuture.get(timeoutSeconds, TimeUnit.SECONDS);
            String recommendAnswer = recommendFuture.get(timeoutSeconds, TimeUnit.SECONDS);

            // 3단계: 합성 — LLM이 세 출력을 하나의 응답으로 합성
            log.info("[Orchestrator] 3단계: 결과 합성");
            String combined = "## 주문/배송 답변\n" + orderAnswer +
                    "\n\n## 정책 설명\n" + policyAnswer +
                    "\n\n## 추천 및 다음 단계\n" + recommendAnswer;

            return chatClient.prompt()
                    .system(SYNTHESIZE_PROMPT)
                    .user("고객 질문: " + question + "\n\n에이전트 답변:\n" + combined)
                    .call()
                    .content();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();  // 인터럽트 상태 복원
            throw new RuntimeException("FAQ 오케스트레이터 인터럽트됨", e);
        } catch (Exception e) {
            log.error("[Orchestrator] 에이전트 실행 실패", e);
            throw new RuntimeException("FAQ 오케스트레이터 워크플로우 실패: " + e.getMessage(), e);
        }
    }

    // ── 체인 결과 합성 ──────────────────────────────────────────────

    private String synthesize(String orderAnswer, String policyAnswer, String recommendAnswer) {
        String combined = "## 주문/배송\n" + orderAnswer +
                "\n\n## 정책\n" + policyAnswer +
                "\n\n## 추천\n" + recommendAnswer;

        return chatClient.prompt()
                .system(SYNTHESIZE_PROMPT)
                .user(combined)
                .call()
                .content();
    }
}

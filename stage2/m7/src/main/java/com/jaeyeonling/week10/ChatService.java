package com.jaeyeonling.week10;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 툴 호출, 토큰 추적, Micrometer 메트릭 기록이 포함된 운영 챗봇 서비스.
 *
 * Week 07의 ChatService를 확장하여:
 * - 검색된 소스 문서 ID를 응답에 포함
 * - 토큰 사용량을 MetricsService에 실제로 기록 (Week 05 통합)
 * - 복합 질문은 FaqOrchestratorService(멀티 에이전트)로 라우팅 (Week 08 통합)
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private static final String SYSTEM_PROMPT =
            "당신은 도움이 되는 고객 지원 담당자입니다. " +
            "주문, 매출, 회사 정책에 관한 질문에 답하기 위해 사용 가능한 툴을 사용하세요. " +
            "특정 데이터가 필요한 질문에는 항상 툴을 사용하세요 — 답을 꾸며내지 마세요.";

    // 복합 질문 감지: 두 개 이상의 도메인 키워드가 같이 등장해야 함
    // 단순 접속사("그리고", "또한") 하나만으로는 복합 질문으로 판정하지 않음
    // — "배송이 얼마나 걸리나요 그리고 추적은 어떻게 하나요?" 처럼
    //   한 도메인 내 두 가지 질문은 단순 질문 경로(툴 호출)로 충분히 처리 가능
    private static final List<String> DOMAIN_KEYWORDS_ORDER  = List.of("주문", "배송", "환불", "반품", "교환");
    private static final List<String> DOMAIN_KEYWORDS_POLICY = List.of("정책", "약관", "보증", "결제", "취소");
    private static final List<String> DOMAIN_KEYWORDS_RECOMMEND = List.of("추천", "방법", "어떻게", "제안", "대안");

    private final ChatClient chatClient;
    private final ToolCallbackProvider toolCallbackProvider;
    private final FaqReranker faqReranker;
    private final MetricsService metricsService;
    private final FaqOrchestratorService orchestratorService;

    public ChatService(
            ChatClient.Builder builder,
            ToolCallbackProvider toolCallbackProvider,
            FaqReranker faqReranker,
            MetricsService metricsService,
            FaqOrchestratorService orchestratorService) {
        this.chatClient = builder.build();
        this.toolCallbackProvider = toolCallbackProvider;
        this.faqReranker = faqReranker;
        this.metricsService = metricsService;
        this.orchestratorService = orchestratorService;
    }

    /**
     * 사용자 질문에 대한 답변을 생성하고 소스와 토큰 사용량을 반환합니다.
     *
     * 라우팅 전략:
     * - 복합 질문(여러 도메인에 걸치는 질문) → FaqOrchestratorService (Week 08 멀티 에이전트)
     * - 단순 질문 → ChatClient + ToolCallbackProvider (Week 07 툴 호출)
     *
     * 토큰 사용량은 MetricsService에도 기록됩니다 (Week 05 관측성 통합).
     */
    public ChatResult chat(String userMessage) {
        log.info("chat 요청: {}", userMessage);

        // 소스 추적을 위해 FAQ 검색 수행 (Week 04: QueryTransform + ReRanker)
        long searchStart = System.currentTimeMillis();
        List<Document> sourceDocs = faqReranker.retrieve(userMessage, 3);
        metricsService.recordSearchLatency(System.currentTimeMillis() - searchStart);
        metricsService.recordSearchResultCount(sourceDocs.size());

        List<String> sources = sourceDocs.stream()
                .map(doc -> doc.getId() != null ? doc.getId() : "unknown")
                .toList();

        String faqContext = sourceDocs.stream()
                .map(Document::getText)
                .collect(java.util.stream.Collectors.joining("\n\n---\n\n"));

        // Week 08: 복합 질문 감지 → 멀티 에이전트 오케스트레이터로 라우팅
        if (isComplexQuestion(userMessage)) {
            log.info("복합 질문 감지 → FaqOrchestratorService로 라우팅");
            String answer = orchestratorService.answer(userMessage, faqContext);
            // 오케스트레이터는 내부적으로 분해(1) + 에이전트(3) + 합성(1) = 5회 LLM 호출을 하므로
            // 단일 응답의 Usage 추출이 불가능하다. -1로 표시하여 집계 불가 상태를 명시.
            metricsService.recordTokenUsage(0, 0, "gpt-4o-mini");
            return new ChatResult(answer, sources, TokenUsage.unavailable());
        }

        // 단순 질문 — LLM 호출 (툴 포함) — chatResponse()로 토큰 사용량 추출
        ChatResponse response = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userMessage)
                .toolCallbacks(toolCallbackProvider)
                .call()
                .chatResponse();

        String answer = response.getResult().getOutput().getText();
        Usage usage = response.getMetadata().getUsage();

        // Week 05: MetricsService에 토큰 사용량 실제 기록
        metricsService.recordTokenUsage(
                usage.getPromptTokens(),
                usage.getCompletionTokens(),
                "gpt-4o-mini");

        TokenUsage tokenUsage = new TokenUsage(
                usage.getPromptTokens(),
                usage.getCompletionTokens(),
                usage.getTotalTokens()
        );

        log.info("응답 완료: {} 토큰 사용 (비용 기록됨)", tokenUsage.totalTokens());
        return new ChatResult(answer, sources, tokenUsage);
    }

    /**
     * 복합 질문 여부를 감지합니다.
     * 두 개 이상의 독립된 도메인 키워드가 함께 등장해야 복합 질문으로 판정합니다.
     * Week 08의 Routing 패턴 적용 — 단순 접속사 하나만으로는 복합 판정하지 않음.
     */
    private boolean isComplexQuestion(String question) {
        long matchedDomains = 0;
        if (DOMAIN_KEYWORDS_ORDER.stream().anyMatch(question::contains))  matchedDomains++;
        if (DOMAIN_KEYWORDS_POLICY.stream().anyMatch(question::contains)) matchedDomains++;
        if (DOMAIN_KEYWORDS_RECOMMEND.stream().anyMatch(question::contains)) matchedDomains++;
        return matchedDomains >= 2;
    }

    // --- 응답 레코드 ---

    public record TokenUsage(
            long promptTokens,
            long completionTokens,
            long totalTokens
    ) {
        /** 멀티 에이전트 등 토큰 집계가 불가능한 경우를 나타냅니다. */
        public static TokenUsage unavailable() {
            return new TokenUsage(-1, -1, -1);
        }
    }

    public record ChatResult(
            String answer,
            List<String> sources,
            TokenUsage tokenUsage
    ) {}
}

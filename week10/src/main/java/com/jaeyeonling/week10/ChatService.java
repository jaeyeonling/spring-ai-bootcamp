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
 * 툴 호출과 토큰 사용량 추적이 포함된 운영 챗봇 서비스.
 *
 * Week 07의 ChatService를 확장하여:
 * - 검색된 소스 문서 ID를 응답에 포함
 * - 토큰 사용량(promptTokens, completionTokens, totalTokens)을 추적
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private static final String SYSTEM_PROMPT =
            "당신은 도움이 되는 고객 지원 담당자입니다. " +
            "주문, 매출, 회사 정책에 관한 질문에 답하기 위해 사용 가능한 툴을 사용하세요. " +
            "특정 데이터가 필요한 질문에는 항상 툴을 사용하세요 — 답을 꾸며내지 마세요.";

    private final ChatClient chatClient;
    private final ToolCallbackProvider toolCallbackProvider;
    private final FaqSearchTool faqSearchTool;

    public ChatService(
            ChatClient.Builder builder,
            ToolCallbackProvider toolCallbackProvider,
            FaqSearchTool faqSearchTool) {
        this.chatClient = builder.build();
        this.toolCallbackProvider = toolCallbackProvider;
        this.faqSearchTool = faqSearchTool;
    }

    /**
     * 사용자 질문에 대한 답변을 생성하고 소스와 토큰 사용량을 반환합니다.
     *
     * @param userMessage 사용자의 질문
     * @return answer, sources, tokenUsage를 포함한 ChatResult
     */
    public ChatResult chat(String userMessage) {
        log.info("chat 요청: {}", userMessage);

        // 소스 추적을 위해 FAQ 검색 수행
        List<Document> sourceDocs = faqSearchTool.searchWithSources(userMessage, 3);
        List<String> sources = sourceDocs.stream()
                .map(doc -> doc.getId() != null ? doc.getId() : "unknown")
                .toList();

        // LLM 호출 (툴 포함) — chatResponse()로 토큰 사용량 추출
        ChatResponse response = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userMessage)
                .toolCallbacks(toolCallbackProvider)
                .call()
                .chatResponse();

        String answer = response.getResult().getOutput().getText();
        Usage usage = response.getMetadata().getUsage();

        TokenUsage tokenUsage = new TokenUsage(
                usage.getPromptTokens(),
                usage.getCompletionTokens(),
                usage.getTotalTokens()
        );

        log.info("응답 완료: {} 토큰 사용", tokenUsage.totalTokens());
        return new ChatResult(answer, sources, tokenUsage);
    }

    // --- 응답 레코드 ---

    public record TokenUsage(
            long promptTokens,
            long completionTokens,
            long totalTokens
    ) {}

    public record ChatResult(
            String answer,
            List<String> sources,
            TokenUsage tokenUsage
    ) {}
}

package com.jaeyeonling.stage2.m7;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 운영 FAQ 챗봇 REST 컨트롤러.
 *
 * MISSION.md 요구사항에 따라 answer, sources, tokenUsage를 반환합니다:
 * {
 *   "answer": "30일 이내에 반품이 가능합니다...",
 *   "sources": ["chunk-id-1", "chunk-id-2"],
 *   "tokenUsage": {
 *     "promptTokens": 150,
 *     "completionTokens": 80,
 *     "totalTokens": 230
 *   }
 * }
 */
@RestController
@RequestMapping("/api")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * FAQ 질문 및 주문 조회 질문을 처리하는 통합 엔드포인트.
     * LLM이 자동으로 적절한 툴(FAQ 검색 또는 주문 조회)을 선택합니다.
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        if (request.question() == null || request.question().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        log.info("chat 요청: {}", request.question());
        ChatService.ChatResult result = chatService.chat(request.question());

        ChatResponse response = new ChatResponse(
                result.answer(),
                result.sources(),
                result.tokenUsage()
        );

        return ResponseEntity.ok(response);
    }

    // --- 요청/응답 레코드 ---

    public record ChatRequest(String question) {}

    public record ChatResponse(
            String answer,
            List<String> sources,
            ChatService.TokenUsage tokenUsage
    ) {}
}

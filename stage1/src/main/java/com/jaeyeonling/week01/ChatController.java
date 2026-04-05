package com.jaeyeonling.week01;

/**
 * FAQ 챗봇의 REST API.
 *
 * POST /api/chat 엔드포인트를 구현하세요:
 * 1. 사용자로부터 질문을 받습니다
 * 2. 관련 FAQ 내용을 찾습니다
 * 3. LLM으로 답변을 생성합니다
 * 4. 토큰 사용량과 함께 답변을 반환합니다
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        log.info("[요청] POST /api/chat — question: \"{}\"", request.question());

        // 요청 검증: question이 null이거나 공백이면 400 Bad Request
        if (request.question() == null || request.question().isBlank()) {
            log.warn("[요청 거부] 빈 질문 → 400 Bad Request");
            return ResponseEntity.badRequest().build();
        }

        try {
            ChatResponse response = chatService.answer(request.question());
            log.info("[응답] 200 OK — answer 길이: {} 글자, 토큰: {}", response.answer().length(), response.tokenUsage().totalTokens());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[응답] 500 Internal Server Error — {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    public record ChatRequest(String question) {}

    public record ChatResponse(
        String answer,
        TokenUsage tokenUsage
    ) {}

    public record TokenUsage(
        int promptTokens,
        int completionTokens,
        int totalTokens
    ) {}
}

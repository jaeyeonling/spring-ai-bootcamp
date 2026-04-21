package com.jaeyeonling.stage2.m8;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 멀티턴 대화를 위한 REST 컨트롤러.
 *
 * 세션 ID를 기반으로 이전 대화 맥락을 유지합니다.
 */
@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        if (request.question() == null || request.question().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (request.sessionId() == null || request.sessionId().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        String answer = chatService.chat(request.sessionId(), request.question());
        return ResponseEntity.ok(new ChatResponse(answer, request.sessionId()));
    }

    public record ChatRequest(String sessionId, String question) {}

    public record ChatResponse(String answer, String sessionId) {}
}

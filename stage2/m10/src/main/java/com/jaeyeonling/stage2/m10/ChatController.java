package com.jaeyeonling.stage2.m10;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 구조화 출력을 반환하는 REST 컨트롤러.
 *
 * LLM 응답을 ChatAnswer 레코드 형태로 직접 반환합니다.
 */
@RestController
@RequestMapping("/api")
public class ChatController {

    private final StructuredOutputService structuredOutputService;

    public ChatController(StructuredOutputService structuredOutputService) {
        this.structuredOutputService = structuredOutputService;
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatAnswer> chat(@RequestBody ChatRequest request) {
        if (request.question() == null || request.question().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        ChatAnswer answer = structuredOutputService.ask(request.question());
        return ResponseEntity.ok(answer);
    }

    public record ChatRequest(String question) {}
}

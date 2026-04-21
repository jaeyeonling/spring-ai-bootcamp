package com.jaeyeonling.stage2.m7;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Docker 환경에서 앱이 정상 동작하는지 확인하기 위한 간단한 채팅 API.
 *
 * 이 컨트롤러는 인프라 검증용입니다:
 * - 앱 컨테이너가 OpenAI에 연결 가능한지
 * - 환경 변수가 올바르게 주입되었는지
 */
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

        if (request.question() == null || request.question().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            String answer = chatService.ask(request.question());
            return ResponseEntity.ok(new ChatResponse(answer));
        } catch (Exception e) {
            log.error("[에러] 채팅 처리 실패: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    public record ChatRequest(String question) {}

    public record ChatResponse(String answer) {}
}

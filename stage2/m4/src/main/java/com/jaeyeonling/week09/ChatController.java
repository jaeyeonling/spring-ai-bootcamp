package com.jaeyeonling.week07;

/**
 * RAG 기반 질문과 툴 호출 질문을 모두 처리하는 REST 컨트롤러.
 *
 * HTTP 요청/응답만 담당합니다. LLM 호출과 툴 선택은 ChatService,
 * 검증 로직은 ReflectionService에 위임합니다.
 *
 * LLM이 툴 간에 자동으로 라우팅하므로 수동 if/else 로직이 없습니다.
 * 새 툴을 추가해도 이 클래스를 수정할 필요가 없습니다.
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
    private final ReflectionService reflectionService;

    public ChatController(ChatService chatService, ReflectionService reflectionService) {
        this.chatService = chatService;
        this.reflectionService = reflectionService;
    }

    /**
     * 단순 채팅 엔드포인트 — LLM이 자동으로 올바른 툴을 선택합니다.
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        if (request.question() == null || request.question().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        log.info("chat 요청: {}", request.question());
        String answer = chatService.chat(request.question());

        return ResponseEntity.ok(new ChatResponse(answer, false));
    }

    /**
     * Reflection이 포함된 채팅 엔드포인트 — 반환 전에 답변을 검증합니다.
     */
    @PostMapping("/chat/verified")
    public ResponseEntity<ChatResponse> chatWithReflection(@RequestBody ChatRequest request) {
        if (request.question() == null || request.question().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        log.info("chat/verified 요청: {}", request.question());
        String answer = reflectionService.chatWithReflection(request.question());

        return ResponseEntity.ok(new ChatResponse(answer, true));
    }

    public record ChatRequest(String question) {}

    public record ChatResponse(
        String answer,
        boolean verified
    ) {}
}

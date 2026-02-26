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

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        // TODO: 요청 검증 (question이 비어있으면 안 됨)
        // TODO: chatService.answer()를 호출하고 결과를 반환
        // TODO: 에러를 우아하게 처리 (스택 트레이스가 아닌 500 메시지 반환)

        throw new UnsupportedOperationException("구현하세요 — mission/MISSION.md 참고");
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

package com.jaeyeonling.stage2.m9;

import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;

/**
 * SSE 스트리밍 응답을 위한 REST 컨트롤러.
 *
 * 토큰 단위로 LLM 응답을 스트리밍하여 사용자 경험을 개선합니다.
 */
@RestController
@RequestMapping("/api/chat")
public class StreamingChatController {

    private final StreamingChatService streamingChatService;

    public StreamingChatController(StreamingChatService streamingChatService) {
        this.streamingChatService = streamingChatService;
    }

    /**
     * SSE 스트리밍 엔드포인트.
     *
     * TODO: 구현하세요
     * - StreamingChatService를 호출하여 Flux<ServerSentEvent<String>>를 반환합니다.
     * - produces = MediaType.TEXT_EVENT_STREAM_VALUE를 설정합니다.
     * - 에러 발생 시 에러 이벤트를 전송합니다.
     *
     * @param question 사용자 질문
     * @return 토큰 단위 SSE 스트림
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamChat(@RequestParam String question) {
        throw new UnsupportedOperationException("구현하세요");
    }
}

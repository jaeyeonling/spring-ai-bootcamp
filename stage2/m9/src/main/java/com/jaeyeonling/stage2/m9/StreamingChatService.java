package com.jaeyeonling.stage2.m9;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;

/**
 * 토큰 단위 스트리밍 응답을 처리하는 서비스.
 *
 * ChatClient의 stream() API를 사용하여 토큰이 생성될 때마다
 * ServerSentEvent로 클라이언트에 전달합니다.
 */
@Service
public class StreamingChatService {

    private static final Logger log = LoggerFactory.getLogger(StreamingChatService.class);

    private final ChatClient chatClient;

    public StreamingChatService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * 토큰 단위 스트리밍을 수행합니다.
     *
     * TODO: 구현하세요
     * - ChatClient의 .stream().content()를 사용하여 Flux<String>을 얻습니다.
     * - 각 토큰을 ServerSentEvent로 감싸서 반환합니다.
     * - 스트리밍 완료 시 [DONE] 이벤트를 전송합니다.
     * - 에러 발생 시 에러 이벤트를 전송합니다.
     * - doOnCancel()로 클라이언트 연결 끊김을 로깅합니다.
     *
     * @param question 사용자 질문
     * @return ServerSentEvent 스트림
     */
    public Flux<ServerSentEvent<String>> streamChat(String question) {
        throw new UnsupportedOperationException("구현하세요");
    }
}

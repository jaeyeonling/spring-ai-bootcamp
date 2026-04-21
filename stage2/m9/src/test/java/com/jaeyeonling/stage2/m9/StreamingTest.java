package com.jaeyeonling.stage2.m9;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SSE 스트리밍 엔드포인트 검증 테스트.
 *
 * 실행: ./gradlew :stage2:m9:test
 *
 * 사전 조건:
 *   - 환경변수에 OPENAI_API_KEY가 설정되어 있어야 합니다
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StreamingTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    @DisplayName("SSE 엔드포인트가 text/event-stream 콘텐츠 타입을 반환한다")
    void sseEndpointReturnsEventStreamContentType() {
        webTestClient.get()
            .uri("/api/chat/stream?question=안녕하세요")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM);
    }

    @Test
    @DisplayName("스트리밍이 여러 개의 이벤트를 생성한다")
    void streamingProducesMultipleEvents() {
        List<String> events = webTestClient.get()
            .uri("/api/chat/stream?question=대한민국의 수도는 어디인가요?")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk()
            .returnResult(String.class)
            .getResponseBody()
            .collectList()
            .block();

        // 스트리밍이므로 2개 이상의 이벤트가 있어야 함
        assertThat(events).hasSizeGreaterThan(1);
    }

    @Test
    @DisplayName("빈 질문에 대해 에러 응답을 반환한다")
    void emptyQuestionReturnsError() {
        webTestClient.get()
            .uri("/api/chat/stream?question=")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isBadRequest();
    }
}

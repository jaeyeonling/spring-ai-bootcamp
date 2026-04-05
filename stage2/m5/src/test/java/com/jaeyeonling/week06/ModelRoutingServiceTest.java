package com.jaeyeonling.week06;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Week 06 테스트 — 모델 라우팅과 프로파일 기반 공급자 전환.
 *
 * 기본 프로파일(OpenAI)로 실행:
 *   ./gradlew :week06:test
 *
 * Ollama 프로파일로 실행:
 *   ./gradlew :week06:test --args='--spring.profiles.active=ollama'
 *
 * 참고: 이 테스트들은 OPENAI_API_KEY 또는 실행 중인 Ollama 인스턴스가 필요합니다.
 */
@SpringBootTest
class ModelRoutingServiceTest {

    @Autowired
    private ModelRoutingService modelRoutingService;

    @Test
    @DisplayName("ModelRoutingService가 주입 가능하다")
    void serviceIsInjectable() {
        assertThat(modelRoutingService).isNotNull();
    }

    @Test
    @DisplayName("getActiveProvider가 비어있지 않은 공급자 이름을 반환한다")
    void activeProviderIsNotEmpty() {
        // 구현 완료 후 이 테스트가 통과해야 합니다.
        // getActiveProvider()가 ChatModel의 클래스명을 반환하는지 검증합니다.
        String provider = modelRoutingService.getActiveProvider();
        assertThat(provider).isNotBlank();
        // 기본 프로파일에서는 OpenAi, ollama 프로파일에서는 Ollama가 예상됩니다
        assertThat(provider).containsAnyOf("OpenAi", "Ollama");
    }

    @Test
    @DisplayName("chat이 비어있지 않은 응답을 반환한다")
    void chatReturnsResponse() {
        // 구현 완료 후 이 테스트가 통과해야 합니다.
        // 실제 LLM 호출이므로 OPENAI_API_KEY 또는 Ollama가 필요합니다.
        String response = modelRoutingService.chat("2 더하기 2는 얼마인가요?");
        assertThat(response).isNotBlank();
        assertThat(response).containsIgnoringCase("4");
    }

    @Test
    @DisplayName("chatWithMetadata가 토큰 사용량이 포함된 ChatResponse를 반환한다")
    void chatWithMetadataReturnsTokenUsage() {
        // 구현 완료 후 이 테스트가 통과해야 합니다.
        var response = modelRoutingService.chatWithMetadata(
            "당신은 도움이 되는 담당자입니다.",
            "대한민국의 수도는 어디인가요?"
        );
        assertThat(response).isNotNull();
        assertThat(response.getResult().getOutput().getText()).containsIgnoringCase("서울");
        assertThat(response.getMetadata().getUsage().getTotalTokens()).isGreaterThan(0);
    }

    @Test
    @DisplayName("기본 프로파일로 애플리케이션이 성공적으로 시작된다")
    void applicationContextLoads() {
        // 이 테스트가 통과하면 Spring 컨텍스트가 모든 빈과 함께 올바르게 로드된 것
        assertThat(modelRoutingService).isNotNull();
    }
}

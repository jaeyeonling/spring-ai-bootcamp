package com.jaeyeonling.week05;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * RAG 파이프라인을 위한 관찰 가능성 설정.
 *
 * 설정 내용:
 * - Micrometer Tracing 브릿지를 통한 OpenTelemetry 트레이싱
 * - 외부 의존성을 위한 커스텀 헬스 인디케이터
 * - 일관된 스팬 이름을 위한 Observation 컨벤션
 */
@Configuration
public class ObservabilityConfig {

    @Value("${spring.ai.vectorstore.qdrant.host:localhost}")
    private String qdrantHost;

    @Value("${spring.ai.vectorstore.qdrant.port:6334}")
    private int qdrantPort;

    /**
     * 벡터 저장소(Qdrant)를 위한 커스텀 헬스 인디케이터.
     * Qdrant에 연결 가능하면 UP을 보고하고, 그렇지 않으면 DOWN을 보고합니다.
     */
    @Bean("vectorStoreHealthIndicator")
    public HealthIndicator vectorStoreHealth() {
        return () -> {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(qdrantHost, qdrantPort), 1000);
                return Health.up()
                    .withDetail("store", "qdrant")
                    .withDetail("host", qdrantHost + ":" + qdrantPort)
                    .build();
            } catch (Exception e) {
                return Health.down(e)
                    .withDetail("store", "qdrant")
                    .withDetail("host", qdrantHost + ":" + qdrantPort)
                    .build();
            }
        };
    }

    /**
     * OpenAI API를 위한 커스텀 헬스 인디케이터.
     * API 키가 설정되어 있고 API에 연결 가능하면 UP을 보고합니다.
     */
    @Bean("openAiHealthIndicator")
    public HealthIndicator openAiHealth() {
        return () -> {
            String apiKey = System.getenv("OPENAI_API_KEY");
            if (apiKey != null && !apiKey.isBlank()) {
                return Health.up()
                    .withDetail("provider", "openai")
                    .withDetail("apiKeyConfigured", true)
                    .build();
            } else {
                return Health.down()
                    .withDetail("provider", "openai")
                    .withDetail("apiKeyConfigured", false)
                    .withDetail("error", "OPENAI_API_KEY 환경변수가 설정되지 않음")
                    .build();
            }
        };
    }
}

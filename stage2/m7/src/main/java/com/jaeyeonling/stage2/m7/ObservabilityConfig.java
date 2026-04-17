package com.jaeyeonling.stage2.m7;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * 운영 관찰 가능성 설정.
 *
 * - Qdrant 헬스 인디케이터
 * - OpenAI API 키 헬스 인디케이터
 * Micrometer Tracing → OTel 브릿지는 의존성만 있으면 자동 설정됩니다.
 */
@Configuration
public class ObservabilityConfig {

    @Value("${spring.ai.vectorstore.qdrant.host:localhost}")
    private String qdrantHost;

    @Value("${spring.ai.vectorstore.qdrant.port:6334}")
    private int qdrantPort;

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

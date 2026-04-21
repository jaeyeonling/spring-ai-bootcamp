package com.jaeyeonling.stage2.m7;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 커스텀 헬스 인디케이터 — 외부 서비스 연결 상태 확인.
 *
 * Docker Compose의 depends_on + healthcheck와 연동하여
 * 모든 의존 서비스가 준비된 후에만 앱이 트래픽을 받도록 합니다.
 *
 * 학습 포인트:
 * - HealthIndicator 인터페이스 구현
 * - Actuator /actuator/health 엔드포인트에 자동 등록
 * - Docker HEALTHCHECK와의 연동
 */
@Configuration
public class HealthCheckConfig {

    /**
     * TODO: Qdrant 연결 상태를 확인하는 HealthIndicator를 구현하세요.
     *
     * 힌트:
     * - Qdrant REST API (GET /collections)를 호출하여 응답 확인
     * - 또는 VectorStore에 간단한 쿼리를 날려 연결 확인
     * - 정상이면 Health.up().build(), 실패하면 Health.down().withException(e).build()
     */
    @Bean
    public HealthIndicator qdrantHealthIndicator() {
        // TODO: 구현하세요
        throw new UnsupportedOperationException("구현하세요");
    }
}

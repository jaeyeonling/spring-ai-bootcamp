package com.jaeyeonling.stage2.m7;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * M7 — 인프라 (Docker Compose 멀티 서비스 오케스트레이션).
 *
 * 학습 목표:
 * - Docker Compose 멀티 서비스 구성 및 의존성 관리
 * - Spring Boot 프로파일 분리 (dev/prod)
 * - 서비스 헬스체크와 depends_on 전략
 * - ETL 데이터 영속화 (재시작 시 재적재 방지)
 * - 환경 변수 관리 (.env)
 *
 * 실행: docker compose up --build
 */
@SpringBootApplication
public class Stage2M7Application {

    public static void main(String[] args) {
        SpringApplication.run(Stage2M7Application.class, args);
    }
}

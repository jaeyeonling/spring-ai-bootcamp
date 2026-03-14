package com.jaeyeonling.week10;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Week 10 — 운영 AI 서비스.
 *
 * Week 01~09의 모든 기능을 통합한 최종 프로젝트:
 * - 하이브리드 RAG (벡터 검색 + 툴 호출)
 * - 툴 호출 (@Tool: 주문 조회, FAQ 검색)
 * - MCP 서버 (HTTP 모드, /mcp/message)
 * - OpenTelemetry 트레이싱 (Micrometer → OTel 자동 브릿지)
 * - Actuator (health, metrics, prometheus)
 * - 평가 파이프라인 (RelevancyEvaluator + FactCheckingEvaluator)
 *
 * 컴포넌트 빈은 각 클래스에 @Service/@Component/@Configuration으로 정의됩니다:
 * - FaqIngestionService  — 시작 시 FAQ 문서 적재
 * - FaqSearchTool        — FAQ 벡터 검색 툴
 * - OrderTools           — 주문 조회 툴
 * - ToolConfig           — ToolCallbackProvider (ChatClient용)
 * - McpToolConfig        — MethodToolCallbackProvider (MCP 서버용)
 * - ChatService          — LLM 호출 + 토큰 추적
 * - ChatController       — REST API (/api/chat)
 * - EvaluationService    — 배치 평가 파이프라인
 * - MetricsService       — 커스텀 Micrometer 메트릭
 * - ObservabilityConfig  — 헬스 인디케이터
 */
@SpringBootApplication
public class Week10Application {

    public static void main(String[] args) {
        SpringApplication.run(Week10Application.class, args);
    }
}

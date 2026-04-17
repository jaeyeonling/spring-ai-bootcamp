package com.jaeyeonling.stage2.m7;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Week 10 — 운영 AI 서비스.
 *
 * Week 01~09의 모든 기능을 통합한 최종 프로젝트:
 * - 하이브리드 RAG (FaqReranker: QueryTransform + LLM ReRanker + 벡터 검색)
 * - 툴 호출 (@Tool: 주문 조회, FAQ 검색)
 * - MCP 서버 (HTTP 모드, /mcp/message) — Spring AI MCP 자동설정이 ToolCallbackProvider 빈을 스캔하여 등록
 * - OpenTelemetry 트레이싱 (Micrometer → OTel 자동 브릿지)
 * - Actuator (health, metrics, prometheus)
 * - 평가 파이프라인 (RelevancyEvaluator + FactCheckingEvaluator)
 *
 * 컴포넌트 빈은 각 클래스에 @Service/@Component/@Configuration으로 정의됩니다:
 * - FaqIngestionService  — 시작 시 FAQ 문서 적재
 * - FaqSearchTool        — FAQ 벡터 검색 툴 (@Tool, VectorStore만 의존 — 순환 의존성 방지)
 * - FaqReranker          — QueryTransform + LLM ReRanker 파이프라인 (ChatClient 의존)
 * - OrderTools           — 주문 조회 툴
 * - ToolConfig           — ToolCallbackProvider (ChatClient + MCP 서버 공용)
 * - FaqOrchestratorService — 복합 질문용 멀티 에이전트 오케스트레이터 (Week 08)
 * - ChatService          — LLM 호출 + 라우팅 + 토큰 추적
 * - ChatController       — REST API (/api/chat)
 * - EvaluationService    — 배치 평가 파이프라인
 * - MetricsService       — 커스텀 Micrometer 메트릭
 * - ObservabilityConfig  — 헬스 인디케이터
 */
@SpringBootApplication
public class Stage2M7Application {

    public static void main(String[] args) {
        SpringApplication.run(Stage2M7Application.class, args);
    }
}

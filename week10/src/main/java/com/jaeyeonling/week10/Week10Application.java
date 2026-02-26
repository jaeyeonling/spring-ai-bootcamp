package com.jaeyeonling.week10;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Week 10 — 운영 AI 서비스.
 *
 * 이 애플리케이션은 week 01-09의 모든 내용을 결합합니다:
 * - 고급 검색을 포함한 하이브리드 RAG (week 02-04)
 * - 자동화된 평가 파이프라인 (week 05)
 * - OpenTelemetry 트레이싱 (week 05)
 * - 툴 호출 (week 07)
 * - 멀티 에이전트 워크플로우 (week 08)
 * - MCP 서버 (week 09)
 *
 * TODO: 이전 주차의 컴포넌트를 가져와서 연결하세요.
 *  최소한 다음이 필요합니다:
 *  - 재순위화가 포함된 VectorStore 기반 검색 서비스
 *  - RAG가 포함된 ChatClient 기반 챗봇 서비스
 *  - 최소 2개의 툴 정의 (@Tool 어노테이션된 메서드)
 *  - MCP 서버 설정 (MethodToolCallbackProvider Bean)
 *  - 트레이싱을 위한 OpenTelemetry 설정
 *  - 평가 엔드포인트 또는 CLI 명령
 */
@SpringBootApplication
public class Week10Application {

    public static void main(String[] args) {
        SpringApplication.run(Week10Application.class, args);
    }

    // TODO: 운영 설정을 위한 빈을 정의하세요.
    //
    //  권장 구조:
    //
    //  @Bean
    //  public RetrievalStrategy retrievalStrategy(VectorStore vectorStore, ChatClient.Builder builder) {
    //      // week 04에서 가장 좋은 전략 사용 (예: ReRanking + QueryTransform)
    //  }
    //
    //  @Bean
    //  public ChatbotService chatbotService(ChatClient.Builder builder, RetrievalStrategy retrieval) {
    //      // RAG가 활성화된 챗봇 연결
    //  }
    //
    //  @Bean
    //  public EvaluationService evaluationService(ChatbotService chatbot) {
    //      // week 05의 평가 파이프라인 연결
    //  }
    //
    //  또한 다음도 필요합니다:
    //  - 툴 빈 (week 07)
    //  - MCP 서비스 빈 (week 09)
    //  - 멀티 에이전트 워크플로우 사용 시 에이전트 빈 (week 08)
}

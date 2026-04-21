package com.jaeyeonling.stage2.m5;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 요청별 토큰 사용량과 비용을 추적합니다.
 *
 * 완료 기준:
 * - 모델별 prompt/completion 토큰 사용량 기록
 * - 모델별 단가 기반 비용 계산
 * - 일/월 비용 집계 조회
 */
@Component
public class CostTracker {

    /**
     * 단일 요청의 토큰 사용 기록.
     */
    public record UsageRecord(String model, int promptTokens, int completionTokens, double cost) {
    }

    /**
     * 모델별 토큰 사용량과 비용을 기록합니다.
     *
     * @param model            사용된 모델명 (예: "gpt-4o-mini", "ollama/llama3.2")
     * @param promptTokens     프롬프트 토큰 수
     * @param completionTokens 완성 토큰 수
     */
    public void record(String model, int promptTokens, int completionTokens) {
        // TODO: 구현하세요
        // 1. 모델별 단가 테이블에서 비용 계산
        // 2. UsageRecord를 내부 저장소에 추가
        // 3. MeterRegistry (Micrometer) 카운터 업데이트
        throw new UnsupportedOperationException("구현하세요");
    }

    /**
     * 특정 모델의 총 토큰 사용량을 반환합니다.
     *
     * @param model 모델명
     * @return 총 토큰 수 (prompt + completion)
     */
    public long getTotalTokens(String model) {
        // TODO: 구현하세요
        throw new UnsupportedOperationException("구현하세요");
    }

    /**
     * 특정 모델의 총 비용을 반환합니다.
     *
     * @param model 모델명
     * @return 총 비용 (USD)
     */
    public double getTotalCost(String model) {
        // TODO: 구현하세요
        throw new UnsupportedOperationException("구현하세요");
    }

    /**
     * 전체 모델의 비용 합계를 반환합니다.
     *
     * @return 전체 비용 (USD)
     */
    public double getOverallCost() {
        // TODO: 구현하세요
        throw new UnsupportedOperationException("구현하세요");
    }

    /**
     * 모든 사용 기록을 반환합니다.
     *
     * @return 사용 기록 리스트
     */
    public List<UsageRecord> getAllRecords() {
        // TODO: 구현하세요
        throw new UnsupportedOperationException("구현하세요");
    }

    /**
     * 모델별 비용 요약을 반환합니다.
     *
     * @return 모델명 → 총 비용 맵
     */
    public Map<String, Double> getCostSummaryByModel() {
        // TODO: 구현하세요
        throw new UnsupportedOperationException("구현하세요");
    }
}

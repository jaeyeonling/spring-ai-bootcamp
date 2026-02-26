package com.jaeyeonling.week05;

import java.util.concurrent.atomic.AtomicReference;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * RAG 파이프라인을 위한 커스텀 Micrometer 메트릭.
 *
 * 검색 지연시간, 토큰 비용, 결과 수, 품질 점수,
 * 환각 빈도를 추적합니다.
 */
@Service
public class MetricsService {

    private final MeterRegistry meterRegistry;

    // 타이머
    private final Timer searchLatency;

    // 카운터
    private final Counter tokenUsageCounter;
    private final Counter tokenCostCounter;
    private final Counter hallucinationCounter;

    // 분포 요약
    private final DistributionSummary searchResultCount;

    // 게이지 백킹 값
    private final AtomicReference<Double> qualityScore = new AtomicReference<>(0.0);

    public MetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        this.searchLatency = Timer.builder("rag.search.latency")
            .description("벡터 저장소에서 문서 검색 시간")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry);

        this.tokenUsageCounter = Counter.builder("rag.llm.token.usage")
            .description("LLM 호출로 소비된 총 토큰 수")
            .register(meterRegistry);

        this.tokenCostCounter = Counter.builder("rag.llm.token.cost")
            .description("LLM 호출의 예상 비용 (USD)")
            .baseUnit("USD")
            .register(meterRegistry);

        this.hallucinationCounter = Counter.builder("rag.hallucination.detected")
            .description("탐지된 환각 수")
            .register(meterRegistry);

        this.searchResultCount = DistributionSummary.builder("rag.search.results.count")
            .description("검색당 반환된 문서 수")
            .register(meterRegistry);

        Gauge.builder("rag.answer.quality.score", qualityScore, AtomicReference::get)
            .description("최신 평균 답변 품질 점수 (0.0 ~ 1.0)")
            .register(meterRegistry);
    }

    /**
     * 벡터 저장소 검색의 지연시간을 기록합니다.
     *
     * @param durationMs 검색 시간 (밀리초)
     */
    public void recordSearchLatency(long durationMs) {
        // TODO: searchLatency 타이머를 사용해 시간을 기록하세요.
        // searchLatency.record(Duration.ofMillis(durationMs));

        throw new UnsupportedOperationException("검색 지연시간 기록을 구현하세요");
    }

    /**
     * 검색으로 반환된 문서 수를 기록합니다.
     *
     * @param count 결과 수
     */
    public void recordSearchResultCount(int count) {
        // TODO: searchResultCount 분포 요약을 사용해 수를 기록하세요.
        // searchResultCount.record(count);

        throw new UnsupportedOperationException("검색 결과 수 기록을 구현하세요");
    }

    /**
     * LLM 호출의 토큰 사용량과 예상 비용을 기록합니다.
     *
     * @param promptTokens     프롬프트 토큰 수
     * @param completionTokens 완성 토큰 수
     * @param model            모델 이름 (비용 계산에 사용)
     */
    public void recordTokenUsage(long promptTokens, long completionTokens, String model) {
        // TODO: 총 토큰 수와 예상 비용을 기록하세요.
        //
        // 1단계 — 토큰 사용량 카운터 증가:
        //   long total = promptTokens + completionTokens;
        //   tokenUsageCounter.increment(total);
        //
        // 2단계 — 비용 카운터 계산 및 증가.
        // gpt-4o-mini 비용 단가:
        //   입력:  1M 토큰당 $0.15
        //   출력:  1M 토큰당 $0.60
        //
        //   double cost = (promptTokens * 0.15 / 1_000_000.0)
        //               + (completionTokens * 0.60 / 1_000_000.0);
        //   tokenCostCounter.increment(cost);

        throw new UnsupportedOperationException("토큰 사용량 기록을 구현하세요");
    }

    /**
     * 환각 탐지 카운터를 증가시킵니다.
     */
    public void incrementHallucinationCount() {
        // TODO: hallucinationCounter를 증가시키세요.
        // hallucinationCounter.increment();

        throw new UnsupportedOperationException("환각 카운터 증가를 구현하세요");
    }

    /**
     * 최신 품질 점수 게이지를 업데이트합니다.
     *
     * @param score 0.0 ~ 1.0 사이의 품질 점수
     */
    public void updateQualityScore(double score) {
        // TODO: 품질 점수 게이지를 뒷받침하는 atomic reference를 업데이트하세요.
        // qualityScore.set(score);

        throw new UnsupportedOperationException("품질 점수 업데이트를 구현하세요");
    }
}

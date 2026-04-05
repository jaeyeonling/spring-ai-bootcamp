package com.jaeyeonling.week10;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

/**
 * RAG 파이프라인을 위한 커스텀 Micrometer 메트릭.
 * Week 05의 MetricsService를 그대로 가져옵니다.
 */
@Service
public class MetricsService {

    private final MeterRegistry meterRegistry;

    private final Timer searchLatency;
    private final Counter tokenUsageCounter;
    private final Counter tokenCostCounter;
    private final Counter hallucinationCounter;
    private final DistributionSummary searchResultCount;
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

    public void recordSearchLatency(long durationMs) {
        searchLatency.record(Duration.ofMillis(durationMs));
    }

    public void recordSearchResultCount(int count) {
        searchResultCount.record(count);
    }

    public void recordTokenUsage(long promptTokens, long completionTokens, String model) {
        long total = promptTokens + completionTokens;
        tokenUsageCounter.increment(total);

        // gpt-4o-mini 가격 기준: input $0.15/1M, output $0.60/1M
        double cost = (promptTokens * 0.15 / 1_000_000.0)
                    + (completionTokens * 0.60 / 1_000_000.0);
        tokenCostCounter.increment(cost);
    }

    public void incrementHallucinationCount() {
        hallucinationCounter.increment();
    }

    public void updateQualityScore(double score) {
        qualityScore.set(score);
    }
}

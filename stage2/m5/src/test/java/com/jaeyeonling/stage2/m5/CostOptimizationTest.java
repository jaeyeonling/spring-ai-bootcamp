package com.jaeyeonling.stage2.m5;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 비용 최적화 기능 테스트.
 *
 * CostTracker와 SemanticCache의 핵심 동작을 검증합니다.
 * 학습자가 구현을 완료하면 이 테스트들이 통과해야 합니다.
 */
class CostOptimizationTest {

    @Nested
    @DisplayName("CostTracker")
    class CostTrackerTest {

        private final CostTracker costTracker = new CostTracker();

        @Test
        @DisplayName("토큰 사용량을 기록하고 모델별 총 토큰을 조회할 수 있다")
        void recordsTokenUsagePerModel() {
            // Arrange
            String model = "gpt-4o-mini";
            int promptTokens = 100;
            int completionTokens = 50;

            // Act
            costTracker.record(model, promptTokens, completionTokens);

            // Assert
            assertThat(costTracker.getTotalTokens(model)).isEqualTo(150);
        }

        @Test
        @DisplayName("동일 모델에 여러 번 기록하면 토큰이 누적된다")
        void accumulatesTokensForSameModel() {
            // Arrange
            String model = "gpt-4o-mini";

            // Act
            costTracker.record(model, 100, 50);
            costTracker.record(model, 200, 100);

            // Assert
            assertThat(costTracker.getTotalTokens(model)).isEqualTo(450);
        }

        @Test
        @DisplayName("모델별 비용을 계산한다")
        void calculatesCostPerModel() {
            // Arrange & Act
            costTracker.record("gpt-4o-mini", 1000, 500);

            // Assert — 비용이 0보다 크면 단가 기반 계산이 동작하는 것
            assertThat(costTracker.getTotalCost("gpt-4o-mini")).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("서로 다른 모델의 비용을 독립적으로 추적한다")
        void tracksDifferentModelsIndependently() {
            // Arrange & Act
            costTracker.record("gpt-4o-mini", 1000, 500);
            costTracker.record("ollama/llama3.2", 1000, 500);

            // Assert
            assertThat(costTracker.getTotalTokens("gpt-4o-mini")).isEqualTo(1500);
            assertThat(costTracker.getTotalTokens("ollama/llama3.2")).isEqualTo(1500);
            // 로컬 모델은 비용이 0이어야 함
            assertThat(costTracker.getTotalCost("ollama/llama3.2")).isEqualTo(0.0);
        }

        @Test
        @DisplayName("전체 비용 합계를 조회할 수 있다")
        void calculatesOverallCost() {
            // Arrange & Act
            costTracker.record("gpt-4o-mini", 1000, 500);
            costTracker.record("gpt-4o-mini", 2000, 1000);

            // Assert
            assertThat(costTracker.getOverallCost()).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("모든 사용 기록을 리스트로 반환한다")
        void returnsAllRecords() {
            // Arrange & Act
            costTracker.record("gpt-4o-mini", 100, 50);
            costTracker.record("ollama/llama3.2", 200, 100);

            // Assert
            assertThat(costTracker.getAllRecords()).hasSize(2);
        }

        @Test
        @DisplayName("모델별 비용 요약을 맵으로 반환한다")
        void returnsCostSummaryByModel() {
            // Arrange & Act
            costTracker.record("gpt-4o-mini", 1000, 500);
            costTracker.record("ollama/llama3.2", 1000, 500);

            // Assert
            var summary = costTracker.getCostSummaryByModel();
            assertThat(summary).containsKey("gpt-4o-mini");
            assertThat(summary).containsKey("ollama/llama3.2");
        }

        @Test
        @DisplayName("기록이 없는 모델의 토큰 수는 0이다")
        void returnsZeroForUnknownModel() {
            assertThat(costTracker.getTotalTokens("unknown-model")).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("SemanticCache")
    class SemanticCacheTest {

        private final SemanticCache cache = new SemanticCache();

        @Test
        @DisplayName("캐시에 저장한 후 동일한 질문으로 조회하면 히트한다")
        void cacheHitForIdenticalQuestion() {
            // Arrange
            String question = "배송 기간이 얼마나 되나요?";
            String answer = "일반 배송은 2-3일 소요됩니다.";

            // Act
            cache.put(question, answer);
            var result = cache.get(question);

            // Assert
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(answer);
        }

        @Test
        @DisplayName("유사한 질문으로 조회하면 캐시 히트한다 (유사도 > threshold)")
        void cacheHitForSimilarQuestion() {
            // Arrange
            cache.put("배송 기간이 얼마나 되나요?", "일반 배송은 2-3일 소요됩니다.");

            // Act — 의미가 유사한 질문
            var result = cache.get("배송 얼마나 걸려요?");

            // Assert
            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("관련 없는 질문으로 조회하면 캐시 미스한다 (유사도 < threshold)")
        void cacheMissForUnrelatedQuestion() {
            // Arrange
            cache.put("배송 기간이 얼마나 되나요?", "일반 배송은 2-3일 소요됩니다.");

            // Act — 전혀 다른 질문
            var result = cache.get("반품 절차가 어떻게 되나요?");

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("빈 캐시에서 조회하면 미스한다")
        void cacheMissOnEmptyCache() {
            var result = cache.get("아무 질문");
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("캐시 크기를 정확히 반환한다")
        void returnsCacheSize() {
            // Arrange & Act
            cache.put("질문 1", "답변 1");
            cache.put("질문 2", "답변 2");

            // Assert
            assertThat(cache.size()).isEqualTo(2);
        }

        @Test
        @DisplayName("캐시를 비우면 크기가 0이 된다")
        void clearEmptiesCache() {
            // Arrange
            cache.put("질문", "답변");

            // Act
            cache.clear();

            // Assert
            assertThat(cache.size()).isEqualTo(0);
        }

        @Test
        @DisplayName("유사도 임계값의 기본값은 0.95이다")
        void defaultThresholdIs095() {
            assertThat(cache.getSimilarityThreshold()).isEqualTo(0.95);
        }
    }
}

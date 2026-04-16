package com.jaeyeonling.stage1;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * InMemoryVectorStore 단위 테스트.
 * OpenAI API가 필요하지 않습니다 — 순수 로직을 테스트합니다.
 */
class InMemoryVectorStoreTest {

    @Test
    @DisplayName("동일한 벡터의 코사인 유사도는 1.0이다")
    void identicalVectorsHaveSimilarityOne() {
        float[] a = {1.0f, 2.0f, 3.0f};
        assertThat(InMemoryVectorStore.cosineSimilarity(a, a)).isCloseTo(1.0, within(0.001));
    }

    @Test
    @DisplayName("직교 벡터의 코사인 유사도는 0.0이다")
    void orthogonalVectorsHaveSimilarityZero() {
        float[] a = {1.0f, 0.0f};
        float[] b = {0.0f, 1.0f};
        assertThat(InMemoryVectorStore.cosineSimilarity(a, b)).isCloseTo(0.0, within(0.001));
    }

    @Test
    @DisplayName("반대 방향 벡터의 코사인 유사도는 -1.0이다")
    void oppositeVectorsHaveSimilarityNegativeOne() {
        float[] a = {1.0f, 2.0f, 3.0f};
        float[] b = {-1.0f, -2.0f, -3.0f};
        assertThat(InMemoryVectorStore.cosineSimilarity(a, b)).isCloseTo(-1.0, within(0.001));
    }

    @Test
    @DisplayName("유사한 벡터가 유사하지 않은 벡터보다 높은 유사도를 가진다")
    void similarVectorsRankHigher() {
        float[] query = {1.0f, 1.0f, 0.0f};
        float[] similar = {0.9f, 1.1f, 0.1f};
        float[] dissimilar = {0.0f, 0.0f, 1.0f};

        double simScore = InMemoryVectorStore.cosineSimilarity(query, similar);
        double dissimScore = InMemoryVectorStore.cosineSimilarity(query, dissimilar);

        assertThat(simScore).isGreaterThan(dissimScore);
    }
}

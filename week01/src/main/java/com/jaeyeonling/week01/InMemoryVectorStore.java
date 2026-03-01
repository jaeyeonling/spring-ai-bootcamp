package com.jaeyeonling.week01;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * FAQ 청크를 위한 인메모리 벡터 저장소.
 *
 * 의도적으로 단순하게 만들었습니다 — 외부 데이터베이스도, 프레임워크도 없습니다.
 * 3주차에 재시작 시 데이터가 사라지는 이 방식의 고통을 느끼게 될 겁니다.
 *
 * 지금은 작동합니다: 시작 시 청크를 임베딩하고, 쿼리 시 코사인 유사도로 검색합니다.
 */
@Component
public class InMemoryVectorStore {

    private final List<EmbeddedChunk> store = new ArrayList<>();

    public record EmbeddedChunk(String text, float[] vector) {}

    /**
     * 모든 청크를 임베딩하고 메모리에 저장합니다.
     * FAQ를 로드한 후 시작 시 한 번 호출하세요.
     */
    public void addAll(List<String> texts, EmbeddingModel embeddingModel) {
        // embeddingModel.embed(text) → float[] 벡터 반환
        // 각 청크를 EmbeddedChunk(text, vector) 로 래핑해서 store에 추가
        for (String text : texts) {
            float[] vector = embeddingModel.embed(text);
            store.add(new EmbeddedChunk(text, vector));
        }
    }

    /**
     * 쿼리 벡터와 가장 유사한 상위 K개의 청크를 찾습니다.
     *
     * @param queryVector 임베딩된 질문
     * @param topK 반환할 결과 수
     * @return 유사도 순으로 정렬된 (높은 것 먼저) 가장 유사한 청크들의 텍스트
     */
    public List<String> search(float[] queryVector, int topK) {
        // 모든 청크에 대해 유사도를 계산하고, 내림차순 정렬 후 상위 topK개 텍스트만 반환
        return store.stream()
                .sorted((a, b) -> Double.compare(
                        cosineSimilarity(b.vector(), queryVector),
                        cosineSimilarity(a.vector(), queryVector)))
                .limit(topK)
                .map(EmbeddedChunk::text)
                .toList();
    }

    /**
     * 두 벡터의 코사인 유사도.
     * -1과 1 사이의 값을 반환합니다. 높을수록 더 유사합니다.
     *
     * 공식: dot(a, b) / (|a| * |b|)
     *   dot(a, b)  = Σ a[i] * b[i]         — 내적 (두 벡터가 같은 방향일수록 큰 값)
     *   |v|        = √(Σ v[i]²)            — L2 놈 (벡터의 크기)
     */
    public static double cosineSimilarity(float[] a, float[] b) {
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dot   += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }

        // 영벡터(모든 값이 0)에 대한 안전 처리
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }

        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    public int size() {
        return store.size();
    }
}

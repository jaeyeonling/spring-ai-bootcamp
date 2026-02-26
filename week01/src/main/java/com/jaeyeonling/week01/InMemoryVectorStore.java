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
        // TODO: 각 텍스트에 대해 embeddingModel.embed(text)를 사용해 임베딩 벡터를 생성하고
        //   EmbeddedChunk로 저장하세요.
        //   힌트: embeddingModel.embed(text)는 float[]를 반환합니다

        throw new UnsupportedOperationException("구현하세요");
    }

    /**
     * 쿼리 벡터와 가장 유사한 상위 K개의 청크를 찾습니다.
     *
     * @param queryVector 임베딩된 질문
     * @param topK 반환할 결과 수
     * @return 유사도 순으로 정렬된 (높은 것 먼저) 가장 유사한 청크들의 텍스트
     */
    public List<String> search(float[] queryVector, int topK) {
        // TODO: 저장된 각 청크에 대해 queryVector와의 코사인 유사도를 계산하세요.
        //   유사도 내림차순으로 정렬. 상위 K개 텍스트를 반환.
        //   아래 cosineSimilarity()를 참고하세요.

        throw new UnsupportedOperationException("구현하세요");
    }

    /**
     * 두 벡터의 코사인 유사도.
     * -1과 1 사이의 값을 반환합니다. 높을수록 더 유사합니다.
     */
    public static double cosineSimilarity(float[] a, float[] b) {
        // TODO: 코사인 유사도를 구현하세요.
        //   공식: dot(a, b) / (magnitude(a) * magnitude(b))
        //   여기서 dot(a,b) = sum(a[i] * b[i])
        //   magnitude(x) = sqrt(sum(x[i] * x[i]))

        throw new UnsupportedOperationException("구현하세요");
    }

    public int size() {
        return store.size();
    }
}

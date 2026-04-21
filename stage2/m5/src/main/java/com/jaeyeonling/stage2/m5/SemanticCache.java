package com.jaeyeonling.stage2.m5;

import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 시맨틱 캐싱: 유사한 질문에 대해 캐시된 응답을 반환합니다.
 *
 * 동작 원리:
 * 1. 질문을 임베딩 벡터로 변환
 * 2. 캐시에 저장된 벡터들과 코사인 유사도 비교
 * 3. 유사도가 threshold(기본 0.95) 이상이면 캐시 히트 → 저장된 응답 반환
 * 4. 유사도가 threshold 미만이면 캐시 미스 → LLM 호출 후 결과를 캐시에 저장
 */
@Component
public class SemanticCache {

    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.95;

    /**
     * 캐시된 응답 엔트리.
     */
    public record CacheEntry(String question, String answer, float[] embedding) {
    }

    /**
     * 질문에 대해 캐시를 조회합니다.
     * 유사도가 threshold 이상인 기존 응답이 있으면 반환합니다.
     *
     * @param question 사용자 질문
     * @return 캐시 히트 시 저장된 응답, 미스 시 empty
     */
    public Optional<String> get(String question) {
        // TODO: 구현하세요
        // 1. question을 임베딩 벡터로 변환
        // 2. 캐시 내 모든 엔트리와 코사인 유사도 계산
        // 3. 최대 유사도가 threshold 이상이면 해당 answer 반환
        throw new UnsupportedOperationException("구현하세요");
    }

    /**
     * 질문과 응답을 캐시에 저장합니다.
     *
     * @param question 사용자 질문
     * @param answer   LLM 응답
     */
    public void put(String question, String answer) {
        // TODO: 구현하세요
        // 1. question을 임베딩 벡터로 변환
        // 2. CacheEntry 생성 후 내부 저장소에 추가
        throw new UnsupportedOperationException("구현하세요");
    }

    /**
     * 캐시 크기를 반환합니다.
     *
     * @return 캐시에 저장된 엔트리 수
     */
    public int size() {
        // TODO: 구현하세요
        throw new UnsupportedOperationException("구현하세요");
    }

    /**
     * 캐시를 비웁니다.
     */
    public void clear() {
        // TODO: 구현하세요
        throw new UnsupportedOperationException("구현하세요");
    }

    /**
     * 유사도 임계값을 반환합니다.
     *
     * @return 현재 유사도 임계값
     */
    public double getSimilarityThreshold() {
        return DEFAULT_SIMILARITY_THRESHOLD;
    }
}

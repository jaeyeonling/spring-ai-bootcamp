package com.jaeyeonling.stage2.m2;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * 문서 검색을 위한 전략 인터페이스.
 *
 * Spring 프로파일이나 설정을 통해 구현체를 교체하여
 * 다양한 검색 접근 방식(기준선, 재순위화, 쿼리 변환)을 비교할 수 있습니다.
 */
public interface RetrievalStrategy {

    /**
     * 주어진 쿼리에 대한 관련 문서를 검색합니다.
     *
     * @param query 사용자의 질문
     * @param topK  반환할 문서 수
     * @return 관련성 순으로 정렬된 관련 문서 목록 (가장 관련 있는 것 먼저)
     */
    List<Document> retrieve(String query, int topK);

    /**
     * 이 전략의 사람이 읽을 수 있는 이름을 반환합니다 (평가 보고서에서 사용).
     */
    default String getName() {
        return getClass().getSimpleName();
    }
}

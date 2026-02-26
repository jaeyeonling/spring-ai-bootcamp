package com.jaeyeonling.week02;

import java.util.List;

/**
 * 문서 내용을 청크로 분할하는 전략 인터페이스.
 *
 * 청킹 전략이 다르면 검색 품질도 달라집니다.
 * 이 인터페이스를 구현해 여러 접근 방식을 실험하고
 * 어느 것이 더 나은 답변을 제공하는지 측정하세요.
 *
 * 이것이 Strategy 패턴입니다 — 사용하는 코드를 변경하지 않고
 * 구현체를 교체할 수 있습니다.
 */
public interface ChunkingStrategy {

    /**
     * 주어진 내용을 임베딩과 검색에 적합한 청크로 분할합니다.
     *
     * @param content 분할할 전체 문서 텍스트
     * @return 텍스트 청크 목록, 각각 비어있지 않음
     */
    List<String> split(String content);
}

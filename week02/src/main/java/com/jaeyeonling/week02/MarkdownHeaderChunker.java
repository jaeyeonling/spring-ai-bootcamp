package com.jaeyeonling.week02;

import java.util.List;

/**
 * "###" 헤더로 마크다운 문서를 분할하는 청커.
 *
 * 이것은 1주차의 단순한 방식입니다 — 각 Q&A 쌍이 하나의 청크가 됩니다.
 * 작동하긴 하지만 청크가 상위 섹션 컨텍스트를 잃습니다.
 * 예를 들어 "반품 배송비"에 관한 청크는 "반품 및 교환" 섹션에
 * 속한다는 정보가 없습니다.
 *
 * TokenWindowChunker와 비교하기 위한 기준선으로 사용하세요.
 */
public class MarkdownHeaderChunker implements ChunkingStrategy {

    @Override
    public List<String> split(String content) {
        // TODO: "###" 헤더로 내용을 분할하세요.
        //   각 청크는 하나의 Q&A 섹션(헤더 + 본문)이어야 합니다.
        //   빈 청크는 필터링하세요.
        //
        //   힌트: "(?=### )"와 같은 정규식으로 구분자를 유지하면서 분할하세요.
        //   참고: 1주차 FaqLoader.splitIntoChunks()

        throw new UnsupportedOperationException("구현하세요");
    }
}

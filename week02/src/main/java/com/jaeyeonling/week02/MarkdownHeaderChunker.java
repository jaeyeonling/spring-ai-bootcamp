package com.jaeyeonling.week02;

import java.util.ArrayList;
import java.util.Arrays;
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
        if (content == null || content.isBlank()) {
            return List.of();
        }

        // "### " 앞에서 split — 각 Q&A 섹션이 하나의 청크
        String[] rawChunks = content.split("(?=### )");
        List<String> result = new ArrayList<>();
        String sectionHeader = "";

        for (String raw : rawChunks) {
            String trimmed = raw.trim();
            if (trimmed.isBlank()) {
                continue;
            }

            if (!trimmed.startsWith("### ")) {
                // ## 섹션 헤더 — 다음 ### 청크에 붙일 접두사로 보관
                sectionHeader = trimmed;
            } else {
                // ### Q&A 청크 — 섹션 헤더가 있으면 앞에 붙여서 컨텍스트 보존
                if (!sectionHeader.isEmpty()) {
                    result.add(sectionHeader + "\n\n" + trimmed);
                } else {
                    result.add(trimmed);
                }
            }
        }

        return result;
    }
}

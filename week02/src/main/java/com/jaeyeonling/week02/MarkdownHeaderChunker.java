package com.jaeyeonling.week02;

import java.util.ArrayList;
import java.util.List;

/**
 * "###" 헤더로 마크다운 문서를 분할하는 청커.
 *
 * 각 Q&A 쌍(### 섹션)이 하나의 청크가 되며,
 * 상위 ## 섹션 헤더를 접두사로 붙여 카테고리 컨텍스트를 보존합니다.
 * 예: "## 반품 및 교환\n\n### 반품 정책이 어떻게 되나요?\n..."
 *
 * TokenWindowChunker와 비교하기 위한 기준선으로 사용하세요.
 */
public class MarkdownHeaderChunker implements ChunkingStrategy {

    private static final String SECTION_HEADER_PREFIX = "## ";
    private static final String QA_HEADER_PREFIX = "### ";

    @Override
    public List<String> split(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        List<String> chunks = new ArrayList<>();
        String sectionHeader = "";
        StringBuilder currentChunk = new StringBuilder();

        for (String line : content.lines().toList()) {
            if (isSectionHeader(line)) {
                sectionHeader = line.trim();
            } else if (isQaHeader(line)) {
                // 이전 Q&A 청크가 있으면 저장
                addChunkIfPresent(chunks, currentChunk);

                // 새 Q&A 청크 시작 — 섹션 헤더를 접두사로 붙임
                currentChunk = new StringBuilder();
                if (!sectionHeader.isEmpty()) {
                    currentChunk.append(sectionHeader).append("\n\n");
                }
                currentChunk.append(line.trim());
            } else if (currentChunk.length() > 0) {
                // Q&A 본문 라인 추가
                currentChunk.append("\n").append(line.trim());
            }
        }

        // 마지막 청크 저장
        addChunkIfPresent(chunks, currentChunk);

        return chunks;
    }

    private boolean isSectionHeader(String line) {
        return line.trim().startsWith(SECTION_HEADER_PREFIX)
                && !line.trim().startsWith(QA_HEADER_PREFIX);
    }

    private boolean isQaHeader(String line) {
        return line.trim().startsWith(QA_HEADER_PREFIX);
    }

    private void addChunkIfPresent(List<String> chunks, StringBuilder chunk) {
        String text = chunk.toString().trim();
        if (!text.isEmpty()) {
            chunks.add(text);
        }
    }
}

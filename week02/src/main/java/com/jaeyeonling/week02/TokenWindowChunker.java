package com.jaeyeonling.week02;

import java.util.ArrayList;
import java.util.List;

/**
 * 오버랩이 있는 토큰 개수 창으로 내용을 분할하는 청커.
 *
 * 문서 구조(헤더)로 분할하는 대신 고정된 토큰 수로 분할합니다.
 * 연속적인 청크들은 겹치는 영역을 공유하므로
 * 청크 경계에서 컨텍스트가 손실되지 않습니다.
 *
 * chunkSize=5, overlap=2인 예시:
 *   청크 1: [A B C D E]
 *   청크 2: [D E F G H]     ← 청크 1과 D, E를 공유
 *   청크 3: [G H I J K]     ← 청크 2와 G, H를 공유
 *
 * 단어를 토큰의 대략적인 대체로 사용합니다.
 */
public class TokenWindowChunker implements ChunkingStrategy {

    private final int chunkSize;
    private final int overlap;

    /**
     * @param chunkSize 청크당 대략적인 토큰 수 (예: 300)
     * @param overlap   연속 청크 간에 공유되는 토큰 수 (예: 50)
     */
    public TokenWindowChunker(int chunkSize, int overlap) {
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    @Override
    public List<String> split(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        String[] words = content.split("\\s+");
        if (words.length <= chunkSize) {
            return List.of(content.trim());
        }

        List<String> chunks = new ArrayList<>();
        int step = chunkSize - overlap;
        int start = 0;

        while (start < words.length) {
            int end = Math.min(start + chunkSize, words.length);
            String chunk = joinWords(words, start, end);

            if (!chunk.isBlank()) {
                chunks.add(chunk);
            }

            start += step;
        }

        return chunks;
    }

    private String joinWords(String[] words, int from, int to) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < to; i++) {
            if (i > from) {
                sb.append(" ");
            }
            sb.append(words[i]);
        }
        return sb.toString();
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public int getOverlap() {
        return overlap;
    }
}

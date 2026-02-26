package com.jaeyeonling.week02;

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
 * Spring AI는 이 개념을 구현한 TokenTextSplitter를 제공합니다.
 * 직접 사용하거나 로직을 직접 구현할 수 있습니다.
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
        // TODO: 내용을 `chunkSize` 토큰 정도의 겹치는 창으로 분할하세요.
        //
        //   옵션 A — Spring AI의 TokenTextSplitter 사용:
        //     import org.springframework.ai.transformer.splitter.TokenTextSplitter;
        //     TokenTextSplitter splitter = new TokenTextSplitter(chunkSize, ...);
        //     List<Document> docs = splitter.apply(List.of(new Document(content)));
        //     return docs.stream().map(Document::getText).toList();
        //
        //   옵션 B — 직접 구현:
        //     1. 내용을 단어로 분할 (토큰의 대략적인 대체)
        //     2. `chunkSize` 단어의 청크 생성
        //     3. 각 새 청크는 이전 청크보다 `chunkSize - overlap` 단어 뒤에서 시작
        //     4. 빈 청크 필터링
        //
        //   어느 방법이든 괜찮습니다. 핵심은 오버랩이 왜 도움이 되는지 이해하는 것입니다.

        throw new UnsupportedOperationException("구현하세요");
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public int getOverlap() {
        return overlap;
    }
}

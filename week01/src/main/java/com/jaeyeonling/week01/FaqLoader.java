package com.jaeyeonling.week01;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * FAQ 문서를 로드하고 청크로 분할합니다.
 *
 * "청크"는 LLM의 컨텍스트로 활용할 수 있을 만큼 작은 텍스트 조각입니다.
 * 분할 방식이 중요합니다 — 너무 크면 토큰을 낭비하고,
 * 너무 작으면 컨텍스트를 잃습니다.
 */
@Component
public class FaqLoader {

    /**
     * FAQ 파일을 읽어 전체 내용을 반환합니다.
     */
    public String loadDocument(String filePath) {
        try {
            Path path = Path.of(filePath);
            if (!Files.exists(path)) {
                throw new IllegalArgumentException("FAQ 파일을 찾을 수 없습니다: " + filePath);
            }
            return Files.readString(path);
        } catch (IOException e) {
            throw new RuntimeException("FAQ 파일 읽기 실패: " + filePath, e);
        }
    }

    /**
     * FAQ 내용을 청크로 분할합니다.
     *
     * FAQ는 각 질문 제목에 "###"을 사용하는 마크다운 형식입니다.
     * 단순 전략: "###"으로 분할하여 각 청크가 하나의 Q&A 쌍이 되도록 합니다.
     *
     * @param content 전체 FAQ 문서 텍스트
     * @return 텍스트 청크 목록, 각 청크는 하나의 Q&A 섹션을 포함
     */
    public List<String> splitIntoChunks(String content) {
        // "### "를 구분자로 분할 — 각 Q&A 섹션을 하나의 청크로 만든다.
        // split()이 구분자를 버리므로, 분할 후 "### "를 다시 붙여서 헤더를 보존한다.
        return Arrays.stream(content.split("(?=### )"))
                .map(String::trim)
                .filter(chunk -> !chunk.isBlank())
                .toList();
    }
}

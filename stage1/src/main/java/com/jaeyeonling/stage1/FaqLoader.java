package com.jaeyeonling.stage1;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

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
     * 디렉토리 내 모든 .md 파일을 읽어 하나의 문서로 합칩니다.
     */
    public String loadDirectory(String dirPath) {
        Path dir = Path.of(dirPath);
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("디렉토리를 찾을 수 없습니다: " + dirPath);
        }

        try (Stream<Path> paths = Files.list(dir)) {
            return paths
                    .filter(p -> p.toString().endsWith(".md"))
                    .sorted()
                    .map(this::readFile)
                    .reduce((a, b) -> a + "\n\n" + b)
                    .orElseThrow(() -> new IllegalStateException("디렉토리에 .md 파일이 없습니다: " + dirPath));
        } catch (IOException e) {
            throw new RuntimeException("디렉토리 읽기 실패: " + dirPath, e);
        }
    }

    private String readFile(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new RuntimeException("파일 읽기 실패: " + path, e);
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

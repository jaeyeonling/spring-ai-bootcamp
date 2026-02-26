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
        // TODO: 파일을 읽어 문자열로 반환하세요.
        //   파일이 존재하지 않는 경우를 처리하세요 (명확한 에러 발생).

        throw new UnsupportedOperationException("구현하세요");
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
        // TODO: 내용을 의미 있는 청크로 분할하세요.
        //   단순 방법: "### "로 분할 (각 Q&A 섹션)
        //   빈 청크는 필터링.
        //   더 나은 컨텍스트를 위해 상위 섹션 헤더 (## 배송, ## 반품 등)를
        //   포함하는 것도 좋습니다 — 하지만 1주차에서는 선택 사항입니다.

        throw new UnsupportedOperationException("구현하세요");
    }
}

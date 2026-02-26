package com.jaeyeonling.week01;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * FaqLoader 단위 테스트.
 * OpenAI API가 필요하지 않습니다.
 */
class FaqLoaderTest {

    private final FaqLoader faqLoader = new FaqLoader();

    @Test
    @DisplayName("splitIntoChunks가 FAQ 내용에서 비어있지 않은 청크를 생성한다")
    void splitProducesNonEmptyChunks() {
        String content = """
            ## 배송
            
            ### 배송은 얼마나 걸리나요?
            표준 배송은 영업일 기준 3-5일이 소요됩니다.
            
            ### 해외 배송이 가능한가요?
            현재 국내 배송만 가능합니다.
            
            ## 반품
            
            ### 반품 정책이 어떻게 되나요?
            30일 이내에 어떤 상품이든 반품할 수 있습니다.
            """;

        List<String> chunks = faqLoader.splitIntoChunks(content);

        assertThat(chunks)
            .isNotEmpty()
            .allSatisfy(chunk -> assertThat(chunk).isNotBlank());
    }

    @Test
    @DisplayName("각 청크에 질문과 답변이 포함된다")
    void eachChunkContainsQuestionAndAnswer() {
        String content = """
            ### 배송은 얼마나 걸리나요?
            표준 배송은 영업일 기준 3-5일이 소요됩니다.
            
            ### 반품 정책이 어떻게 되나요?
            30일 이내에 어떤 상품이든 반품할 수 있습니다.
            """;

        List<String> chunks = faqLoader.splitIntoChunks(content);

        assertThat(chunks).hasSizeGreaterThanOrEqualTo(2);
        assertThat(chunks.get(0)).containsIgnoringCase("배송");
    }
}

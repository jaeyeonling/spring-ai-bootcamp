package com.jaeyeonling.week02;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * 청킹 전략 단위 테스트.
 * OpenAI API가 필요하지 않습니다 — 순수 분할 로직을 테스트합니다.
 */
class ChunkingStrategyTest {

    private static final String SAMPLE_FAQ = """
            ## 배송

            ### 배송은 얼마나 걸리나요?
            표준 배송은 영업일 기준 3-5일이 소요됩니다.
            익스프레스 배송은 3,000원에 익일 배송이 가능합니다.

            ### 해외 배송이 가능한가요?
            현재 불가합니다. 일본 및 동남아시아로의 해외 배송은
            2026년 2분기에 예정되어 있습니다.

            ## 반품 및 교환

            ### 반품 정책이 어떻게 되나요?
            배송 후 30일 이내에 어떤 상품이든 반품할 수 있습니다.

            ### 반품 배송비는 얼마인가요?
            환불 금액에서 2,500원이 공제됩니다.
            결함이나 당사의 실수로 인한 반품은 반품 배송비를 부담하지 않습니다.

            ## 계정

            ### 어떻게 가입하나요?
            우측 상단의 회원가입을 클릭하세요. 이메일로 가입하거나
            카카오, 네이버, 구글 소셜 로그인을 사용할 수 있습니다.
            """;

    @Nested
    @DisplayName("MarkdownHeaderChunker")
    class MarkdownHeaderChunkerTests {

        private final MarkdownHeaderChunker chunker = new MarkdownHeaderChunker();

        @Test
        @DisplayName("FAQ 내용에서 비어있지 않은 청크를 생성한다")
        void producesNonEmptyChunks() {
            List<String> chunks = chunker.split(SAMPLE_FAQ);

            assertThat(chunks)
                    .isNotEmpty()
                    .allSatisfy(chunk -> assertThat(chunk).isNotBlank());
        }

        @Test
        @DisplayName("각 청크에 질문 헤딩이 포함된다")
        void eachChunkContainsQuestion() {
            List<String> chunks = chunker.split(SAMPLE_FAQ);

            assertThat(chunks)
                    .allSatisfy(chunk -> assertThat(chunk).contains("###"));
        }

        @Test
        @DisplayName("예상한 수의 섹션으로 분할된다")
        void correctNumberOfChunks() {
            List<String> chunks = chunker.split(SAMPLE_FAQ);

            // 샘플에는 ### 헤더 아래 5개의 Q&A 섹션이 있습니다
            assertThat(chunks).hasSizeGreaterThanOrEqualTo(5);
        }

        @Test
        @DisplayName("빈 내용을 우아하게 처리한다")
        void handlesEmptyContent() {
            List<String> chunks = chunker.split("");

            assertThat(chunks).isEmpty();
        }
    }

    @Nested
    @DisplayName("TokenWindowChunker")
    class TokenWindowChunkerTests {

        @Test
        @DisplayName("FAQ 내용에서 비어있지 않은 청크를 생성한다")
        void producesNonEmptyChunks() {
            TokenWindowChunker chunker = new TokenWindowChunker(50, 10);

            List<String> chunks = chunker.split(SAMPLE_FAQ);

            assertThat(chunks)
                    .isNotEmpty()
                    .allSatisfy(chunk -> assertThat(chunk).isNotBlank());
        }

        @Test
        @DisplayName("대략적인 청크 크기 제한을 준수한다")
        void respectsChunkSizeLimits() {
            int chunkSize = 30;
            TokenWindowChunker chunker = new TokenWindowChunker(chunkSize, 5);

            List<String> chunks = chunker.split(SAMPLE_FAQ);

            // 각 청크의 단어 수가 청크 크기 근방이어야 합니다
            // (단어를 토큰의 대략적인 대체로 사용)
            for (String chunk : chunks) {
                int wordCount = chunk.split("\\s+").length;
                // 어느 정도 허용 — 마지막 청크는 더 짧을 수 있으며,
                // 단어 != 토큰이므로 관대한 상한선을 사용합니다
                assertThat(wordCount)
                        .as("청크 단어 수가 청크 크기를 크게 초과하지 않아야 합니다: \"%s...\"",
                                chunk.substring(0, Math.min(50, chunk.length())))
                        .isLessThanOrEqualTo(chunkSize * 2);
            }
        }

        @Test
        @DisplayName("청크 크기가 클수록 청크 수가 적어진다")
        void largerChunkSizeProducesFewerChunks() {
            TokenWindowChunker small = new TokenWindowChunker(30, 5);
            TokenWindowChunker large = new TokenWindowChunker(100, 10);

            List<String> smallChunks = small.split(SAMPLE_FAQ);
            List<String> largeChunks = large.split(SAMPLE_FAQ);

            assertThat(smallChunks.size()).isGreaterThan(largeChunks.size());
        }

        @Test
        @DisplayName("오버랩으로 청크들이 경계 내용을 공유한다")
        void overlapSharesBoundaryContent() {
            TokenWindowChunker chunker = new TokenWindowChunker(20, 5);

            List<String> chunks = chunker.split(SAMPLE_FAQ);

            if (chunks.size() >= 2) {
                // 오버랩으로 인해 N번째 청크의 끝과 N+1번째 청크의 시작이
                // 일부 단어를 공유해야 합니다
                String firstChunk = chunks.get(0);
                String secondChunk = chunks.get(1);

                // 첫 번째 청크의 마지막 몇 단어 가져오기
                String[] firstWords = firstChunk.split("\\s+");
                if (firstWords.length > 5) {
                    String lastWord = firstWords[firstWords.length - 1];
                    // 겹치는 단어가 다음 청크에 나타나야 합니다
                    assertThat(secondChunk).contains(lastWord);
                }
            }
        }

        @Test
        @DisplayName("빈 내용을 우아하게 처리한다")
        void handlesEmptyContent() {
            TokenWindowChunker chunker = new TokenWindowChunker(50, 10);

            List<String> chunks = chunker.split("");

            assertThat(chunks).isEmpty();
        }

        @Test
        @DisplayName("청크 크기보다 짧은 내용을 처리한다")
        void handlesShortContent() {
            TokenWindowChunker chunker = new TokenWindowChunker(1000, 100);

            List<String> chunks = chunker.split("이것은 짧은 문장입니다.");

            assertThat(chunks).hasSize(1);
            assertThat(chunks.get(0)).contains("짧은 문장");
        }
    }
}

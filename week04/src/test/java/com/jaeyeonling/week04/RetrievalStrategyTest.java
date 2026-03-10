package com.jaeyeonling.week04;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 검색 전략 평가 베이스 클래스.
 *
 * 정답 판정 방식: LLM 기반 관련성 판정 (실무 기준)
 *   - expected_answer의 핵심 정보가 검색된 문서에 포함되어 있는지 LLM에게 질문
 *   - "YES" / "NO"로만 응답하도록 프롬프트 설계
 *   - 단순 키워드 매칭이나 전체 문서 임베딩 유사도보다 정확
 *
 * 각 전략별 테스트 클래스가 이 클래스를 상속하여 @ActiveProfiles만 다르게 선언한다.
 */
@SpringBootTest
abstract class RetrievalStrategyTest {

    @Autowired
    RetrievalStrategy retrievalStrategy;

    @Autowired
    ChatClient.Builder chatClientBuilder;

    @Value("${data.test-questions-path}")
    String testQuestionsPath;

    @Test
    @DisplayName("전략이 활성 프로파일에 따라 주입된다")
    void strategyIsInjected() {
        assertThat(retrievalStrategy).isNotNull();
        assertThat(retrievalStrategy).isInstanceOf(RetrievalStrategy.class);
    }

    @Test
    @DisplayName("retrieve가 유효한 쿼리에 대해 비어있지 않은 결과를 반환한다")
    void retrieveReturnsResults() {
        List<Document> results = retrievalStrategy.retrieve("환불은 어떻게 받나요?", 5);

        assertThat(results).isNotEmpty();
        assertThat(results).hasSizeLessThanOrEqualTo(5);
    }

    @Test
    @DisplayName("retrieve가 topK 파라미터를 준수한다")
    void retrieveRespectsTopK() {
        List<Document> top1 = retrievalStrategy.retrieve("반품 정책이 어떻게 되나요?", 1);
        List<Document> top5 = retrievalStrategy.retrieve("반품 정책이 어떻게 되나요?", 5);

        assertThat(top1).hasSize(1);
        assertThat(top5).hasSizeLessThanOrEqualTo(5);
        assertThat(top5.size()).isGreaterThanOrEqualTo(top1.size());
    }

    @Test
    @DisplayName("검색된 문서에 비어있지 않은 내용이 있다")
    void retrievedDocumentsHaveContent() {
        List<Document> results = retrievalStrategy.retrieve("결제 방법", 3);

        for (Document doc : results) {
            assertThat(doc.getText()).isNotBlank();
        }
    }

    @Test
    @DisplayName("전략에 읽을 수 있는 이름이 있다")
    void strategyHasName() {
        assertThat(retrievalStrategy.getName()).isNotBlank();
    }

    // -----------------------------------------------------------------------
    // 평가 테스트 — 전략 정확도 비교
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("[평가] 현재 전략의 Top-1 및 Top-5 정확도 측정")
    void evaluateCurrentStrategy() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<TestQuestion> questions = mapper.readValue(
                new File(testQuestionsPath),
                new TypeReference<>() {});

        int top1Hits = 0;
        int top5Hits = 0;
        long totalLatency = 0;

        for (TestQuestion q : questions) {
            long start = System.currentTimeMillis();
            List<Document> results = retrievalStrategy.retrieve(q.question(), 5);
            totalLatency += System.currentTimeMillis() - start;

            if (containsAnswer(results.subList(0, 1), q.expectedAnswer())) {
                top1Hits++;
            }
            if (containsAnswer(results, q.expectedAnswer())) {
                top5Hits++;
            }
        }

        int total = questions.size();
        double top1Accuracy = 100.0 * top1Hits / total;
        double top5Accuracy = 100.0 * top5Hits / total;
        long avgLatency = totalLatency / total;

        System.out.println();
        System.out.printf("=== 평가 결과: %s ===%n", retrievalStrategy.getName());
        System.out.printf("총 질문 수  : %d%n", total);
        System.out.printf("Top-1 정확도: %.1f%% (%d/%d)%n", top1Accuracy, top1Hits, total);
        System.out.printf("Top-5 정확도: %.1f%% (%d/%d)%n", top5Accuracy, top5Hits, total);
        System.out.printf("평균 지연시간: %d ms%n", avgLatency);
        System.out.println();

        assertThat(retrievalStrategy.getName()).isNotBlank();
    }

    boolean containsAnswer(List<Document> docs, String expectedAnswer) {
        // 실무 기준: LLM에게 "이 문서들에 예상 답변의 핵심 정보가 포함되어 있는가?"를 질문
        String context = docs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n---\n\n"));

        String prompt = """
                다음 문서들을 읽고, 예상 답변의 핵심 정보가 문서에 포함되어 있는지 판단하세요.

                예상 답변: %s

                문서:
                %s

                위 문서들에 예상 답변의 핵심 정보(숫자, 날짜, 고유명사, 핵심 사실)가 포함되어 있으면 YES,
                없으면 NO로만 답하세요.
                """.formatted(expectedAnswer, context);

        String response = chatClientBuilder.build()
                .prompt()
                .user(prompt)
                .call()
                .content()
                .trim()
                .toUpperCase();

        return response.startsWith("YES");
    }

    public record TestQuestion(
            String question,
            @JsonProperty("expected_answer") String expectedAnswer
    ) {}
}

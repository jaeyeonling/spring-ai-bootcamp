package com.jaeyeonling.week04;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 검색 전략 테스트.
 *
 * 이 테스트들은 각 전략 구현이 올바르게 작동하는지 검증합니다.
 * 실행: ./gradlew :week04:test
 *
 * 참고: 이 테스트들은 실제 OpenAI API를 호출하며 실행 중인 Qdrant 인스턴스가 필요합니다.
 * OPENAI_API_KEY가 설정되어 있고 Qdrant가 localhost:6334에서 실행 중이어야 합니다.
 */
@SpringBootTest
@ActiveProfiles("baseline")
class RetrievalStrategyTest {

    @Autowired
    private RetrievalStrategy retrievalStrategy;

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
    // 이것들은 단위 테스트보다 벤치마크에 가깝습니다. 전략을 평가하고
    // 비교 표를 채우기 위해 수동으로 실행하세요.
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("[평가] 현재 전략의 Top-1 및 Top-5 정확도 측정")
    void evaluateCurrentStrategy() {
        // TODO: ../../data/test_questions.json에서 테스트 질문 로드
        //
        // 각 테스트 질문에 대해:
        // 1. retrievalStrategy.retrieve(question, 5) 실행
        // 2. 예상 답변 문서가 1위에 있는지 확인 (Top-1)
        // 3. 예상 답변 문서가 1-5위 안에 있는지 확인 (Top-5)
        //
        // 결과 출력:
        // System.out.printf("전략: %s%n", retrievalStrategy.getName());
        // System.out.printf("Top-1 정확도: %.1f%%%n", top1Accuracy);
        // System.out.printf("Top-5 정확도: %.1f%%%n", top5Accuracy);

        // 플레이스홀더 — 테스트 질문 로드 후 구현
        assertThat(retrievalStrategy).isNotNull();
    }
}

package com.jaeyeonling.week02;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.chat.evaluation.RelevancyEvaluator;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * 테스트 질문 세트를 대상으로 챗봇을 자동 평가합니다.
 *
 * test_questions.json에서 질문을 로드하고, 각 질문을 챗봇에 전송하고,
 * Spring AI의 RelevancyEvaluator를 사용해 관련성을 평가하고,
 * 요약 보고서를 출력합니다.
 *
 * 이것이 AI 엔지니어링을 체계적으로 만드는 피드백 루프입니다:
 *   [변경 적용] → [평가 실행] → [실패 내용 읽기] → [변경 적용] → ...
 */
@Component
public class EvaluationRunner {

    private final ChatService chatService;
    private final RelevancyEvaluator evaluator;
    private final ObjectMapper objectMapper;

    public EvaluationRunner(ChatService chatService, ChatClient.Builder chatClientBuilder) {
        this.chatService = chatService;
        this.objectMapper = new ObjectMapper();

        // RelevancyEvaluator는 LLM을 통해 답변 품질을 판단하기 위해 ChatClient를 사용합니다.
        // 질문, 예상 답변, 실제 답변을 LLM에 보내 관련성을 점수화합니다.
        this.evaluator = new RelevancyEvaluator(chatClientBuilder);
    }

    /**
     * JSON 파일에서 테스트 질문을 로드합니다.
     *
     * @param filePath test_questions.json 경로
     * @return 예상 답변이 포함된 테스트 질문 목록
     */
    public List<TestQuestion> loadTestQuestions(String filePath) throws IOException {
        // TODO: Jackson ObjectMapper를 사용해 JSON 파일을 역직렬화하세요.
        //   파일은 "question"과 "expected_answer" 필드를 가진 객체의 JSON 배열입니다.
        //   objectMapper.readValue(new File(filePath), new TypeReference<List<TestQuestion>>() {}) 사용

        throw new UnsupportedOperationException("구현하세요");
    }

    /**
     * 모든 테스트 질문에 대해 평가를 실행하고 보고서를 출력합니다.
     *
     * @param testQuestions 평가할 질문들
     */
    public EvaluationReport runEvaluation(List<TestQuestion> testQuestions) {
        // TODO: 각 테스트 질문에 대해:
        //   1. chatService.answerQuestion(tq.question())을 호출해 챗봇의 답변 가져오기
        //   2. 다음으로 EvaluationRequest 구성:
        //      - 질문 (사용자 쿼리)
        //      - Document로 래핑된 예상 답변 (평가기를 위한 컨텍스트)
        //      - 실제 답변 (생성된 응답)
        //   3. evaluator.evaluate(request)를 호출해 EvaluationResponse 얻기
        //   4. response.isPass()로 답변의 관련성 여부 확인
        //   5. 통과/실패 수 추적
        //   6. 실패한 질문을 세부 정보와 함께 로그에 기록 (예상 vs 실제, 피드백)
        //
        //   모든 질문 처리 후 정확도를 계산하고 EvaluationReport를 반환하세요.

        throw new UnsupportedOperationException("구현하세요");
    }

    /**
     * 형식화된 평가 보고서를 콘솔에 출력합니다.
     */
    public void printReport(EvaluationReport report) {
        // TODO: 다음을 보여주는 명확한 요약을 출력하세요:
        //   - 총 질문 수
        //   - 통과 수
        //   - 실패 수
        //   - 정확도 백분율
        //   - 세부 정보가 포함된 실패 질문 목록
        //
        //   출력 예시:
        //   === 평가 보고서 ===
        //   총계: 30 | 통과: 22 | 실패: 8 | 정확도: 73.3%
        //
        //   실패:
        //     Q: "일본으로 배송이 가능한가요?"
        //     예상: "현재 불가합니다. 해외 배송..."
        //     실제: "표준 및 익스프레스 배송을 제공합니다..."
        //     피드백: 답변이 해외 배송을 다루지 않습니다...

        throw new UnsupportedOperationException("구현하세요");
    }

    /**
     * 예상 답변이 포함된 단일 테스트 질문.
     */
    public record TestQuestion(
            String question,
            @JsonProperty("expected_answer") String expectedAnswer
    ) {}

    /**
     * 평가 실행의 요약.
     */
    public record EvaluationReport(
            int total,
            int passed,
            int failed,
            double accuracy,
            List<FailedQuestion> failures
    ) {}

    /**
     * 단일 평가 실패의 세부 정보.
     */
    public record FailedQuestion(
            String question,
            String expectedAnswer,
            String actualAnswer,
            String feedback
    ) {}
}

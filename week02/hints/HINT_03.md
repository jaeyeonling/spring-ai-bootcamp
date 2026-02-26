# 힌트 03: RelevancyEvaluator를 사용한 자동화된 평가

## 문제

테스트 질문이 30개 있습니다. 변경할 때마다(새 청킹 전략, 새 프롬프트) 30개를 모두 재테스트해야 합니다. 수동으로 답변을 읽는 것은:
- 느립니다 (10분 이상 소요)
- 일관성이 없습니다 (피로도에 따라 판단이 달라짐)
- 반복 불가능합니다 (30개를 정말 꼼꼼히 읽었나요?)

자동화된 방법이 필요합니다: **"이 답변이 예상 답변과 관련 있나요?"**

## Spring AI의 RelevancyEvaluator

Spring AI는 LLM을 사용해 답변 품질을 판단하는 평가기를 제공합니다.
`RelevancyEvaluator`는 생성된 답변이 제공된 컨텍스트에서 사용자의 질문과
관련 있는지 확인합니다.

```java
import org.springframework.ai.chat.evaluation.RelevancyEvaluator;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.chat.client.ChatClient;

RelevancyEvaluator evaluator = new RelevancyEvaluator(chatClientBuilder);
```

## EvaluationRequest / EvaluationResponse 패턴

`EvaluationRequest`는 다음을 묶습니다:
- **사용자 쿼리** (질문된 내용)
- **컨텍스트** (검색되어 사용된 청크)
- **응답 내용** (생성된 답변)

```java
import org.springframework.ai.document.Document;

// 요청 구성
EvaluationRequest request = new EvaluationRequest(
    userQuery,                                          // 질문
    List.of(new Document(context)),                     // 검색된 컨텍스트
    generatedAnswer                                     // 챗봇의 답변
);

// 평가
EvaluationResponse response = evaluator.evaluate(request);

// 결과 확인
boolean passed = response.isPass();
float score = response.getScore();
String feedback = response.getFeedback();
```

## 배치 평가 실행

30개 질문 모두 평가하는 패턴:

```java
public void runEvaluation(List<TestQuestion> testQuestions) {
    int passed = 0;
    int failed = 0;

    for (TestQuestion tq : testQuestions) {
        // 1. 챗봇을 통해 질문 전송
        String answer = chatService.answerQuestion(tq.question());

        // 2. 평가 요청 구성
        //    여기서 "context"는 사용된 검색된 청크
        //    평가기는 컨텍스트가 주어졌을 때 답변이 관련 있는지 확인
        EvaluationRequest request = new EvaluationRequest(
            tq.question(),
            List.of(new Document(tq.expectedAnswer())),
            answer
        );

        // 3. 평가
        EvaluationResponse response = evaluator.evaluate(request);

        // 4. 결과 기록
        if (response.isPass()) {
            passed++;
        } else {
            failed++;
            System.out.printf("실패: %s%n", tq.question());
            System.out.printf("  예상: %s%n", tq.expectedAnswer());
            System.out.printf("  실제: %s%n", answer);
            System.out.printf("  피드백: %s%n", response.getFeedback());
        }
    }

    double accuracy = (double) passed / testQuestions.size() * 100;
    System.out.printf("%n결과: %d/%d 통과 (%.1f%%)%n", passed, testQuestions.size(), accuracy);
}
```

## JSON에서 테스트 질문 로드

Jackson을 사용해 테스트 질문 로드:

```java
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

record TestQuestion(String question, @JsonProperty("expected_answer") String expectedAnswer) {}

ObjectMapper mapper = new ObjectMapper();
List<TestQuestion> questions = mapper.readValue(
    new File("../data/test_questions.json"),
    new TypeReference<List<TestQuestion>>() {}
);
```

## 중요 참고사항

1. **각 평가 호출은 토큰을 소비합니다** — 평가기 자체도 LLM을 사용합니다.
   30개 질문으로 평가 실행당 LLM 호출이 30번 추가됩니다.

2. **평가기는 완벽하지 않습니다** — LLM이 다른 LLM을 평가합니다.
   일부 엣지 케이스는 오판될 수 있습니다. 개발 반복에는 괜찮습니다.

3. **실패를 로그로 남기세요** — 실패한 질문이 무엇을 수정해야 하는지 정확히 알려줍니다.
   실패 10개 중 8개가 배송 관련이라면, 배송 청크를 개선해야 합니다.

4. **변경할 때마다 평가를 실행하세요** — 이것이 피드백 루프입니다:
   ```
   청킹 변경 → 평가 실행 → 정확도 확인
   프롬프트 변경 → 평가 실행 → 정확도 확인
   둘 다 변경 → 평가 실행 → 정확도 확인
   ```

## 평가 루프

이것이 AI 엔지니어링의 핵심 워크플로우입니다:

```
[변경 적용] → [자동 평가 실행] → [실패 내용 분석] → [변경 적용] → ...
```

테스트 주도 개발과 같지만 AI 시스템을 위한 버전입니다.
차이점: "테스트"가 결정론적이 아닌 확률적입니다.
75% 통과율은 허용 가능할 수 있습니다. 100% 통과율은 비현실적입니다.

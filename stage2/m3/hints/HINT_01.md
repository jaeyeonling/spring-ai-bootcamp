# 힌트 01: 실패 원인 분류 — 검색 실패 vs 생성 실패

"왜 틀렸는가"를 모르면 엉뚱한 곳을 개선하게 됩니다.

## 두 가지 실패 유형

```
케이스 A: 검색은 맞게 했는데 LLM이 잘못 답함
  검색 결과: "마켓플레이스 항목은 셀러 정책 따름" ← 정답 포함
  LLM 답변: "모든 상품 14일 반품 가능" ← 생성 실패

케이스 B: 검색 자체가 실패
  검색 결과: [상담로그, 구버전 정책, 무관한 FAQ]
  LLM 답변: "해당 내용을 찾을 수 없습니다" ← 검색 실패
```

케이스 A는 프롬프트와 가드레일로 개선합니다.
케이스 B는 M2 검색 전략으로 개선합니다.

## 자동 분류 구현

```java
@Service
public class FailureDiagnostics {

    public DiagnosticResult diagnose(
            String question,
            String answer,
            List<Document> retrievedDocs,
            String expectedAnswer) {

        // 1. 검색 문서에 정답 힌트가 있었는가?
        boolean retrievalHadAnswer = checkRetrievalQuality(question, retrievedDocs, expectedAnswer);

        // 2. 최종 답변이 올바른가?
        boolean answerIsCorrect = evaluateAnswer(question, answer, expectedAnswer);

        if (answerIsCorrect) {
            return DiagnosticResult.CORRECT;
        } else if (retrievalHadAnswer) {
            return DiagnosticResult.GENERATION_FAILURE;  // 검색은 맞았는데 생성 실패
        } else {
            return DiagnosticResult.RETRIEVAL_FAILURE;   // 검색 자체 실패
        }
    }

    private boolean checkRetrievalQuality(
            String question, List<Document> docs, String expectedAnswer) {
        // 검색 결과에 expected_answer의 핵심 정보가 있는지 LLM으로 판단
        String check = chatClient.prompt()
            .user("""
                질문: %s
                기대 답변의 핵심: %s
                검색된 문서들: %s

                검색된 문서들이 기대 답변을 도출하는 데 필요한 정보를 포함하고 있는가?
                JSON: {"contains_answer": true/false}
                """.formatted(question, expectedAnswer,
                    docs.stream().map(Document::getText).collect(Collectors.joining("\n---\n"))))
            .call()
            .entity(RetrievalQualityCheck.class);

        return check.containsAnswer();
    }

    record RetrievalQualityCheck(boolean containsAnswer) {}
}
```

## 배치 분석

100개 테스트 질문 전체를 분석하여 실패 유형 분포를 파악합니다:

```java
Map<DiagnosticResult, Long> distribution = testQuestions.stream()
    .map(q -> diagnostics.diagnose(q.question(), runRag(q), q.expectedAnswer(), q.sources()))
    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

// 예상 결과:
// CORRECT: 38건 (38%)
// RETRIEVAL_FAILURE: 42건 (42%)
// GENERATION_FAILURE: 20건 (20%)
```

이 분포가 개선 우선순위를 결정합니다.
검색 실패가 많으면 M2로, 생성 실패가 많으면 프롬프트 엔지니어링부터.

# 힌트 02: FAQ 후보 생성과 정책 일관성 검증

## FAQ 후보 생성

패턴에서 좋은 답변을 추출하여 FAQ 형식으로 변환합니다.

```java
@Service
public class FaqCandidateGenerator {

    public FaqCandidate generate(RepeatedPattern pattern) {
        // 패턴에서 정확한 답변만 선별 (agent_accuracy=correct)
        List<ChatLog> goodCases = pattern.cases().stream()
            .filter(log -> "correct".equals(log.agentAccuracy()))
            .toList();

        if (goodCases.isEmpty()) {
            log.info("정확한 답변 없음, 패턴 건너뜀: {}", pattern.intent());
            return null;
        }

        String examples = goodCases.stream()
            .limit(3)  // 최대 3개 예시
            .map(log -> "고객: " + log.customerQuestion()
                     + "\n상담사: " + log.agentAnswer())
            .collect(Collectors.joining("\n\n"));

        String faqContent = chatClient.prompt()
            .system("""
                고객 상담 사례를 분석하여 공식 FAQ 항목을 작성하세요.

                요구사항:
                1. ### 으로 시작하는 질문
                2. 명확하고 간결한 답변 (2-4문장)
                3. 상담사 표현이 아닌 공식 문서 스타일
                4. 개인 정보, 주문번호 등 제거
                5. 정책 문서에 근거한 내용만

                형식:
                ### [질문]
                [답변]
                """)
            .user("상담 사례:\n" + examples)
            .call()
            .content();

        return new FaqCandidate(pattern.intent(), faqContent, goodCases.size());
    }
}
```

## 정책 일관성 검증

생성된 FAQ가 현행 정책과 충돌하지 않는지 확인합니다.

```java
@Service
public class PolicyConsistencyValidator {

    public ValidationResult validate(FaqCandidate candidate) {
        // 관련 정책 문서 검색
        List<Document> relatedPolicies = vectorStore.similaritySearch(
            SearchRequest.query(candidate.content())
                .withTopK(3)
                .withFilterExpression("layer == 'policy' AND status == 'current'")
        );

        if (relatedPolicies.isEmpty()) {
            return ValidationResult.NEEDS_REVIEW("관련 현행 정책 문서 없음");
        }

        String policyText = relatedPolicies.stream()
            .map(Document::getText)
            .collect(Collectors.joining("\n\n"));

        ValidationCheck check = chatClient.prompt()
            .user("""
                [신규 FAQ 후보]
                %s

                [현행 정책 문서]
                %s

                신규 FAQ가 현행 정책과 일관되는지 검증하세요.
                특히 기간, 금액, 조건 등 구체적 수치가 일치하는지 확인하세요.

                JSON으로 응답:
                {
                  "consistent": true/false,
                  "conflicts": ["구체적인 충돌 내용"],
                  "recommendation": "approve" | "reject" | "review",
                  "reason": "판단 이유"
                }
                """.formatted(candidate.content(), policyText))
            .call()
            .entity(ValidationCheck.class);

        return switch (check.recommendation()) {
            case "approve" -> ValidationResult.APPROVED(check.reason());
            case "reject"  -> ValidationResult.REJECTED(check.conflicts().toString());
            default        -> ValidationResult.NEEDS_REVIEW(check.reason());
        };
    }

    record ValidationCheck(
        boolean consistent,
        List<String> conflicts,
        String recommendation,
        String reason
    ) {}
}
```

## 사람 검토 큐

자동 승인이 불확실한 경우 사람이 검토합니다:

```java
public void process(FaqCandidate candidate) {
    ValidationResult result = validator.validate(candidate);

    switch (result.status()) {
        case APPROVED -> {
            vectorStore.add(List.of(toDocument(candidate)));
            log.info("FAQ 자동 추가: {}", candidate.intent());
        }
        case REJECTED -> {
            log.info("FAQ 거부됨: {} — {}", candidate.intent(), result.reason());
        }
        case NEEDS_REVIEW -> {
            reviewQueue.add(candidate, result.reason());  // 사람 검토 대기
            log.info("FAQ 검토 대기: {}", candidate.intent());
        }
    }
}
```

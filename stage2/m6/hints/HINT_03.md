# 힌트 03: 에이전트 간 모순 처리

FaqAgent와 PolicyAgent가 서로 다른 답을 주는 경우입니다.

```
FaqAgent:    "모든 상품 14일 반품 가능합니다."
PolicyAgent: "마켓플레이스 상품은 셀러 정책에 따라 3-30일 상이합니다."
```

## 모순 감지

```java
private boolean hasConflict(List<AgentResponse> responses) {
    if (responses.size() < 2) return false;

    String conflictCheck = chatClient.prompt()
        .user("""
            다음 답변들이 서로 모순되는가?
            %s

            JSON: {"has_conflict": true/false, "conflict_description": "..."}
            """.formatted(formatResponses(responses)))
        .call()
        .content();

    return parseBoolean(conflictCheck, "has_conflict");
}
```

## 모순 해결 전략

### 전략 1: 정책 우선 (가장 단순)

```java
private String resolveConflict(String question, List<AgentResponse> responses) {
    // PolicyAgent의 답변을 우선, FAQ는 보조
    AgentResponse policy = findByName(responses, "PolicyAgent");
    AgentResponse faq = findByName(responses, "FaqAgent");

    return chatClient.prompt()
        .user("""
            질문: %s

            현행 정책 (우선): %s
            공식 FAQ (참고): %s

            현행 정책을 기준으로 답변하세요.
            정책과 FAQ가 다르다면 정책이 최신 기준임을 명시하세요.
            """.formatted(question, policy.answer(), faq.answer()))
        .call()
        .content();
}
```

### 전략 2: 신뢰도 기반

```java
// 각 Agent의 confidence 값으로 우선순위 결정
AgentResponse primary = responses.stream()
    .max(Comparator.comparingDouble(AgentResponse::confidence))
    .orElseThrow();
```

### 전략 3: 모순을 고객에게 투명하게 전달

```java
// 모순이 심각할 때: 상담사 연결 권유
return """
    이 질문에 대해 내부 정책 문서들이 다른 내용을 담고 있습니다.
    정확한 안내를 위해 고객센터(1234-5678)로 문의해 주시면
    담당자가 직접 확인해 드리겠습니다.
    """;
```

## 에이전트 실패 시 폴백

```java
public AgentResponse process(AgentRequest request) {
    try {
        return doProcess(request);
    } catch (Exception e) {
        log.warn("{} 실패: {}", getName(), e.getMessage());
        return new AgentResponse(
            getName(),
            "해당 소스에서 정보를 찾지 못했습니다.",
            List.of(),
            0.0  // 낮은 신뢰도
        );
    }
}
```

## 성능 최적화: 선택적 병렬 실행

모든 질문에 3개 에이전트를 다 돌리면 비쌉니다.

```java
// 단순 질문: FaqAgent만
// 정책 관련: FaqAgent + PolicyAgent
// 선례 필요: 3개 모두

private List<Agent> selectAgents(String question, QuestionComplexity complexity) {
    return switch (complexity) {
        case SIMPLE   -> List.of(faqAgent);
        case MODERATE -> List.of(faqAgent, policyAgent);
        case COMPLEX  -> List.of(faqAgent, policyAgent, chatHistoryAgent);
    };
}
```

단일 RAG vs FaqAgent만 vs 전체 Orchestrator 세 가지를 비교하여
어떤 케이스에서 멀티 에이전트가 효과적인지 확인하세요.

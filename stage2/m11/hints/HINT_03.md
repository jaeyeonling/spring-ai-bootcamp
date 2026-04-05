# 힌트 03: 벡터 DB 동적 업데이트와 드리프트 감지

## 벡터 DB에 FAQ 동적 추가

```java
private Document toDocument(FaqCandidate candidate) {
    return new Document(
        candidate.content(),
        Map.of(
            "layer", "faq",
            "category", intentToCategory(candidate.intent()),
            "source", "auto-generated",
            "generated_from", "chatlog-pattern",
            "generated_at", LocalDate.now().toString(),
            "intent", candidate.intent(),
            "review_count", String.valueOf(candidate.supportingCaseCount())
        )
    );
}

public void addToKnowledgeBase(FaqCandidate candidate) {
    // 중복 확인: 유사한 FAQ가 이미 있는지
    List<Document> existing = vectorStore.similaritySearch(
        SearchRequest.query(candidate.content())
            .withTopK(1)
            .withSimilarityThreshold(0.92)
            .withFilterExpression("layer == 'faq'")
    );

    if (!existing.isEmpty()) {
        log.info("유사한 FAQ 이미 존재, 추가 건너뜀: {}", candidate.intent());
        return;
    }

    vectorStore.add(List.of(toDocument(candidate)));
    log.info("FAQ 지식 베이스에 추가: {} ({}건 상담 기반)",
        candidate.intent(), candidate.supportingCaseCount());
}
```

## 추가 전후 정확도 비교

```java
public ImprovementReport measureImprovement(FaqCandidate candidate) {
    // 추가 전 관련 질문들 정확도
    List<TestQuestion> relatedQuestions = testQuestions.stream()
        .filter(q -> q.primaryIntent().equals(candidate.intent()))
        .toList();

    double beforeAccuracy = evaluate(relatedQuestions);

    // FAQ 추가
    addToKnowledgeBase(candidate);

    // 추가 후 정확도
    double afterAccuracy = evaluate(relatedQuestions);

    log.info("FAQ 추가 효과 — {} intent: {:.1%} → {:.1%} ({:+.1%}p)",
        candidate.intent(), beforeAccuracy, afterAccuracy, afterAccuracy - beforeAccuracy);

    return new ImprovementReport(candidate.intent(), beforeAccuracy, afterAccuracy);
}
```

## 데이터 드리프트 감지

새로운 유형의 질문이 갑자기 증가하면 알림을 보냅니다:

```java
@Scheduled(cron = "0 0 * * * *")  // 매시간
public void detectDrift() {
    // 최근 1시간 상담 로그
    List<ChatLog> recentLogs = chatLogRepository.findLastHours(1);

    // 기존 intent와 매칭되지 않는 질문
    List<ChatLog> unknownIntents = recentLogs.stream()
        .filter(log -> "unknown".equals(log.primaryIntent())
                    || log.primaryIntent() == null)
        .toList();

    // 1시간에 10건 이상 미분류 질문이면 알림
    if (unknownIntents.size() >= 10) {
        String sample = unknownIntents.stream()
            .limit(3)
            .map(ChatLog::customerQuestion)
            .collect(Collectors.joining(", "));

        log.warn("드리프트 감지: {}건의 미분류 질문 (예: {})", unknownIntents.size(), sample);
        alertService.notify(
            "새로운 유형의 고객 질문이 급증하고 있습니다. 지식 베이스 업데이트를 검토하세요."
        );
    }
}
```

## 피드백 루프 전체 효과 측정

```
시작 시점:
  - FAQ 60개
  - 전체 정확도: 38%
  - Hard tier 정확도: 12%

4주 후 (자동 FAQ 추가 20건):
  - FAQ 80개 (자동 추가 20건 포함)
  - 전체 정확도: ?%
  - Hard tier 정확도: ?%
  - 자동 생성 FAQ 관련 질문 정확도: ?%
```

지식 베이스가 상담 로그로부터 스스로 성장하는 것을 측정하세요.

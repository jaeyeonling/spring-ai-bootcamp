# M11 미션: 데이터 피드백 루프

## Stage 1에서 겪은 벽

상담 로그 3,000건을 보면 이런 패턴이 보입니다:

> "배송 지연 보상 받을 수 있나요?" → agent_kim이 상세히 잘 답변했음
> → 하지만 FAQ에는 없음 → 다음 고객이 물어보면 LLM이 모름

좋은 답변이 상담 로그에 쌓이고 있습니다.
그런데 지식 베이스(FAQ)에는 반영되지 않습니다.
같은 질문이 반복됩니다. 상담사가 매번 새로 답합니다.

---

## 미션

상담 로그에서 반복되는 패턴을 감지하고,
검증된 답변을 자동으로 FAQ에 추가하는 **피드백 루프**를 만드세요.

**선행 조건**: M1 (벡터 DB) + M3 (평가 파이프라인)

### 완료 기준

```
[ ] 상담 로그에서 반복 질문 패턴 자동 감지 (7일 내 3회 이상)
[ ] 감지된 패턴을 FAQ 후보로 자동 변환
[ ] 생성된 FAQ 후보가 기존 정책과 충돌하지 않는지 검증
[ ] 검증 통과한 FAQ를 벡터 DB에 자동 반영
[ ] 반영 전후 정확도 비교
```

---

## 피드백 루프 설계

```
[상담 로그] → [패턴 감지] → [FAQ 후보 생성] → [검증] → [벡터 DB 반영]
                                                  ↓
                                             [검증 실패 → 사람 검토 대기]
```

### 단계 1: 패턴 감지

```java
@Service
public class PatternDetector {

    public List<RepeatedPattern> detect(List<ChatLog> recentLogs) {
        // 같은 intent, 7일 내 3회 이상 등장한 질문 그룹
        return recentLogs.stream()
            .collect(Collectors.groupingBy(log -> log.primaryIntent()))
            .entrySet().stream()
            .filter(e -> e.getValue().size() >= 3)
            .map(e -> new RepeatedPattern(e.getKey(), e.getValue()))
            .toList();
    }
}
```

### 단계 2: FAQ 후보 생성

```java
String faqGenerationPrompt = """
    다음 상담 사례들을 분석하여 FAQ 항목을 생성하세요.

    상담 사례:
    %s

    다음 형식으로 FAQ를 작성하세요:
    ### [질문]
    [답변 — 정확하고 간결하게, 정책 문서 기반]

    주의: 상담사 오답이나 예외 케이스는 포함하지 마세요.
    """.formatted(formatCases(pattern.cases()));
```

### 단계 3: 정책 일관성 검증

생성된 FAQ 후보가 기존 정책 문서와 충돌하는지 확인합니다:

```java
String validationPrompt = """
    [새로운 FAQ 후보]
    %s

    [현행 정책 문서]
    %s

    새로운 FAQ가 현행 정책과 일관되는지 검증하세요.
    JSON으로 응답: {
      "consistent": true/false,
      "conflicts": ["..."],
      "recommendation": "approve" | "reject" | "review"
    }
    """.formatted(faqCandidate, policyDocuments);
```

### 단계 4: 벡터 DB 반영

```java
if (validation.recommendation().equals("approve")) {
    Document newFaq = new Document(
        faqCandidate.content(),
        Map.of(
            "layer", "faq",
            "source", "auto-generated",
            "generated_at", LocalDate.now().toString(),
            "intent", pattern.intent()
        )
    );
    vectorStore.add(List.of(newFaq));
    log.info("FAQ 자동 추가: {}", pattern.intent());
}
```

---

## 데이터 드리프트 감지

새로운 유형의 질문이 갑자기 늘어나면 알림을 보냅니다:

```java
// 이번 주에 처음 등장한 intent가 10건 이상이면
if (newIntentCount >= 10) {
    alertService.notify("새로운 질문 유형 감지: " + newIntent);
}
```

배송 지연, 신규 정책 시행, 이벤트 등으로 갑자기 새로운 질문이 몰릴 수 있습니다.
이것을 자동으로 감지하고 지식 베이스를 선제적으로 업데이트할 수 있습니다.

---

## 힌트

| 힌트 | 이럴 때 열어보세요 |
|------|-------------------|
| `HINT_01.md` | 상담 로그 패턴 감지 및 그룹화가 막힐 때 |
| `HINT_02.md` | FAQ 후보 생성 및 정책 일관성 검증이 막힐 때 |
| `HINT_03.md` | 벡터 DB 동적 업데이트 및 드리프트 감지가 막힐 때 |

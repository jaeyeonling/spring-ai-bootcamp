# M3 미션: 관측성 & 평가

## Stage 1에서 겪은 벽

M1-M2를 진행하면서 이런 상황이 생겼을 것입니다:

> "프롬프트를 바꿨더니 좋아진 것 같은데, 수치로는 확인이 안 된다."
> "어떤 질문에서 틀렸는지 알겠는데, **왜** 틀렸는지 모르겠다."
> "검색이 실패한 건지, LLM이 잘못 생성한 건지 구분이 안 된다."
> "배포하고 나서 품질이 바뀌었는지 어떻게 아는가?"

---

## 미션

RAG 파이프라인의 **실패 원인을 자동으로 분류**하고,
**전략 간 A/B 비교**를 수치로 판단할 수 있게 만드세요.

### 완료 기준

```
[ ] 실패 질문의 원인 자동 분류: 검색 실패 vs 생성 실패
[ ] 환각 탐지: 검색 문서에 없는 내용을 답했는지 감지
[ ] 두 전략(예: M2 Baseline vs ReRanker)을 수치로 비교 가능
[ ] 요청별 비용(토큰 수) 추적
[ ] 답변 범위 제한: FAQ에 없는 질문에 "모르겠습니다" 응답
```

---

## 핵심 개념

### 실패 원인 분류

RAG가 틀렸을 때 두 가지 원인이 있습니다:

```
[검색 실패] 관련 문서가 상위에 없었다
    → 개선 방향: M2 검색 전략

[생성 실패] 관련 문서가 있었는데 LLM이 잘못 답했다
    → 개선 방향: 프롬프트 엔지니어링, 가드레일
```

이 둘을 구분하지 못하면 엉뚱한 곳을 개선하게 됩니다.

```java
public FailureAnalysis diagnose(String question, String answer, List<Document> retrievedDocs) {
    // 1. 검색 결과에 정답이 있었는가?
    boolean retrievalSuccess = containsAnswer(retrievedDocs, question);

    // 2. 정답이 있었는데 LLM이 틀렸는가?
    if (retrievalSuccess && isWrongAnswer(answer)) {
        return FailureAnalysis.GENERATION_FAILURE;
    }

    // 3. 검색 자체가 실패했는가?
    if (!retrievalSuccess) {
        return FailureAnalysis.RETRIEVAL_FAILURE;
    }

    return FailureAnalysis.CORRECT;
}
```

### 환각 탐지

LLM이 검색 문서에 없는 내용을 답했는지 확인합니다.

```java
String hallucinationCheck = """
    [검색 문서]
    %s

    [답변]
    %s

    답변의 모든 사실적 주장이 검색 문서에 근거하는가?
    JSON으로 응답: {"hallucination": true/false, "unsupported_claims": [...]}
    """.formatted(context, answer);
```

### 답변 범위 제한 (가드레일)

```java
// 시스템 프롬프트에 추가
"""
컨텍스트에 답이 없으면 반드시 다음과 같이 답하세요:
"죄송합니다, 해당 내용은 현재 안내드리기 어렵습니다. 고객센터(1234-5678)로 문의해 주세요."

절대로 컨텍스트에 없는 내용을 추측하거나 만들어내지 마세요.
"""
```

---

## 평가 파이프라인 설계 시 주의점

평가 자체가 LLM을 사용하면 비용이 발생합니다.
모든 요청을 실시간으로 평가하면 응답 지연이 28배 증가할 수 있습니다.

**배치 평가**와 **실시간 모니터링**을 분리하세요:
- 배치: 테스트셋 100개를 주기적으로 평가 (품질 추적)
- 실시간: 비용/지연시간만 모니터링 (운영 중 감지)

---

## 힌트

| 힌트 | 이럴 때 열어보세요 |
|------|-------------------|
| `HINT_01.md` | 실패 원인 분류 파이프라인을 어떻게 구현할지 막힐 때 |
| `HINT_02.md` | Spring AI EvaluationRunner, FactCheckingEvaluator 사용법이 필요할 때 |
| `HINT_03.md` | Micrometer 메트릭으로 비용/지연시간 추적이 막힐 때 |

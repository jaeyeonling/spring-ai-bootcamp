# M6 미션: 멀티 에이전트

## Stage 1에서 겪은 벽

이런 질문이 있었을 것입니다:

> "VIP 회원인데 마켓플레이스에서 산 냉장 식품 반품할 수 있나요?"

이 질문에 정확히 답하려면:
- FAQ: "마켓플레이스는 셀러 정책 따름"
- 정책 v3: "냉장 식품은 원칙적으로 비반품"
- 내부 메모: "VIP는 CS팀 재량으로 예외 처리 가능"
- 상담 로그: 유사 사례 선례

하나의 RAG 호출로 이 4가지를 정확히 종합하기 어렵습니다.

---

## 미션

각 데이터 소스를 담당하는 전문 에이전트를 만들고,
교차 소스 질문에서 **Baseline 대비 정확도 +20%p** 를 달성하세요.

### 완료 기준

```
[ ] FaqAgent: Layer 1 FAQ 전담
[ ] PolicyAgent: Layer 2 정책 문서 전담 (버전 판단 포함)
[ ] ChatHistoryAgent: Layer 3 상담 로그에서 유사 선례 탐색
[ ] OrchestratorAgent: 질문을 분석하고 에이전트를 조합하여 최종 답변 생성
[ ] Medium tier 테스트 질문에서 Baseline 대비 +20%p 향상
```

**선행 조건**: M4 완료 (에이전트가 Tool을 사용합니다)

---

## 핵심 설계 결정

### 언제 멀티 에이전트가 필요한가

멀티 에이전트가 항상 답은 아닙니다.

| 상황 | 권장 |
|------|------|
| 단일 소스 FAQ 질문 | 단일 RAG로 충분 |
| 여러 소스 교차 필요 | Orchestrator-Worker |
| 단계별 처리 (검색→검증→응답) | Chain |
| 질문 유형별 분기 | Router |

먼저 단일 에이전트로 해결 안 되는 케이스를 파악하세요.
그 케이스만 멀티 에이전트로 처리하면 됩니다.

### 에이전트 패턴 선택

**Chain 패턴** — 순차 처리, 앞 결과가 뒤의 입력이 됨:
```
질문 → FaqAgent → PolicyAgent(FAQ 결과 참고) → 최종 응답
```

**Orchestrator-Worker** — 병렬 처리, 결과를 합성:
```
질문 → Orchestrator ──→ FaqAgent       ──→ Orchestrator(합성) → 최종 응답
                    ──→ PolicyAgent     ─↗
                    ──→ ChatHistoryAgent─↗
```

교차 소스 질문에는 Orchestrator-Worker가 일반적으로 낫습니다.
하지만 트레이드오프를 직접 비교해보세요.

### 에이전트 간 모순 처리

FaqAgent: "14일 반품 가능"
PolicyAgent: "셀러 정책 따름 (3-30일)"

이 모순을 어떻게 처리할 것인가?

```java
// 옵션 1: Orchestrator가 판단
// 옵션 2: 가장 최신 정책 우선
// 옵션 3: 사용자에게 모순을 노출하고 CS 연결
```

이 결정이 답변의 신뢰도를 결정합니다.

---

## Agent 인터페이스

```java
public interface Agent {
    String getName();
    String getDescription();  // Orchestrator가 어떤 에이전트를 쓸지 판단하는 기준
    AgentResponse process(AgentRequest request);
}

public record AgentRequest(String question, Map<String, Object> context) {}
public record AgentResponse(String answer, List<Document> sources, double confidence) {}
```

---

## 힌트

| 힌트 | 이럴 때 열어보세요 |
|------|-------------------|
| `HINT_01.md` | 언제 멀티 에이전트를 쓸지 판단 기준이 모호할 때 |
| `HINT_02.md` | Orchestrator-Worker 패턴 구현이 막힐 때 |
| `HINT_03.md` | 에이전트 간 모순 발생 시 처리 전략이 막힐 때 |

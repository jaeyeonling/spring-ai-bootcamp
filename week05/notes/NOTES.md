# Week 05 — 관측성: Micrometer, OpenTelemetry, 환각 탐지

> **미션**: 운영 중인 RAG 챗봇에서 잘못된 답변이 신고됐을 때,
> 문제가 검색 단계인지 생성 단계인지 5분 안에 찾을 수 있는 관측성 인프라를 구축하라.

---

## 구현 파일 구조

```
week05/src/main/java/com/jaeyeonling/week05/
├── MetricsService.java       — 6개 커스텀 Micrometer 메트릭 등록 및 기록
├── EvaluationService.java    — 배치 평가 파이프라인 + 환각 탐지 + 인시던트 진단
├── ObservabilityConfig.java  — 커스텀 헬스 인디케이터 (Qdrant, OpenAI)
└── Week05Application.java    — 진입점
```

---

## 왜 관측성인가

Week 04까지는 "답변이 맞는가"를 오프라인 평가로만 측정했다.
실제 운영 환경에서는 **실시간**으로 무슨 일이 일어나는지 알아야 한다.

```
Week 02: RelevancyEvaluator → "이 답변이 관련 있는가?" (배치, 수동)
Week 04: Top-1/Top-5 정확도 → "검색이 올바른 문서를 찾는가?" (배치, 수동)
Week 05: Micrometer + OTel  → "지금 이 순간 무슨 일이 일어나는가?" (실시간, 자동)
```

잘못된 답변이 신고됐을 때 두 가지 질문에 답해야 한다:
1. **검색 실패인가?** — 올바른 문서가 상위에 오지 않았는가
2. **생성 실패인가?** — 올바른 문서가 있었는데 LLM이 잘못 해석했는가

트레이싱 없이는 이 둘을 구분할 수 없다.

---

## 관측성 계층 구조

Spring AI의 관측성은 세 계층으로 이루어진다:

```
Spring AI Observations (최상위)
    │   ChatClient 호출, 벡터 검색 등을 자동으로 Observation으로 감싼다
    ▼
Micrometer Tracing (브릿지)
    │   Observation → 스팬(Span)으로 변환
    │   micrometer-tracing-bridge-otel
    ▼
OpenTelemetry SDK (전송)
    │   스팬을 OTLP 프로토콜로 백엔드에 전송
    │   opentelemetry-exporter-otlp
    ▼
Jaeger / Prometheus / Grafana (시각화)
```

핵심: Spring AI가 Micrometer를 통해 자동으로 스팬을 생성한다.
별도 코드 없이 `ChatClient` 호출이 트레이스에 잡힌다.

---

## 구현 흐름

### MetricsService — 6개 커스텀 메트릭

Spring AI가 자동으로 제공하는 메트릭(`gen_ai.client.token.usage` 등) 외에,
RAG 파이프라인 전용 커스텀 메트릭을 `MeterRegistry`에 직접 등록한다.

```
MeterRegistry (Spring Boot 자동 설정)
    │
    ├─ Timer("rag.search.latency")           → 벡터 검색 시간 (p50, p95, p99)
    ├─ Counter("rag.llm.token.usage")        → 누적 토큰 수
    ├─ Counter("rag.llm.token.cost")         → 누적 비용 (USD)
    ├─ Counter("rag.hallucination.detected") → 환각 탐지 횟수
    ├─ DistributionSummary("rag.search.results.count") → 검색당 반환 문서 수
    └─ Gauge("rag.answer.quality.score")     → 최신 평균 품질 점수 (0.0~1.0)
```

**Gauge 특이사항**: Gauge는 현재 값을 반환하는 함수를 등록하는 방식이다.
값을 직접 저장하는 게 아니라 `AtomicReference<Double>`을 참조하게 하고,
값이 바뀔 때 `AtomicReference.set()`으로 업데이트한다.

```java
// 등록 (생성자에서 1회)
Gauge.builder("rag.answer.quality.score", qualityScore, AtomicReference::get)
    .register(meterRegistry);

// 업데이트 (평가 완료 시마다)
qualityScore.set(newScore);
```

**토큰 비용 계산** (gpt-4o-mini 기준):
```java
double cost = (promptTokens * 0.15 / 1_000_000.0)
            + (completionTokens * 0.60 / 1_000_000.0);
```

---

### EvaluationService — 배치 평가 파이프라인

단일 질문 평가(`evaluateSingle`)가 5단계로 구성된다:

```
질문 (TestCase)
    │
    ▼
1단계: vectorStore.similaritySearch() → 문서 검색
    │   metricsService.recordSearchLatency() 호출
    ▼
2단계: chatClient로 답변 생성
    │   검색된 문서를 컨텍스트로 주입
    ▼
3단계: RelevancyEvaluator.evaluate() → 관련성 판정
    │   질문과 답변이 관련 있는가? (0.0~1.0 점수)
    ▼
4단계: FactCheckingEvaluator.evaluate() → 환각 판정
    │   답변이 검색된 문서의 사실과 일치하는가?
    ▼
5단계: 환각 감지 시 metricsService.incrementHallucinationCount()
    │
    ▼
EvaluationResult (question, answer, documents, relevancyScore, isRelevant, isHallucination)
```

`runBatchEvaluation`은 전체 테스트셋에 위 파이프라인을 반복 실행하고
평균 관련성 점수를 `metricsService.updateQualityScore()`로 내보낸다.

**Observation으로 감싸는 이유**:
```java
Observation.createNotStarted("rag.batch-evaluation", observationRegistry)
    .lowCardinalityKeyValue("batch.size", String.valueOf(testCases.size()))
    .observe(() -> { ... });
```
배치 평가 전체가 하나의 스팬으로 Jaeger에 기록된다.
내부의 `ChatClient` 호출들이 자식 스팬으로 들어간다.

---

### diagnoseIncident — 인시던트 근본 원인 분석

잘못된 답변이 신고됐을 때, 검색 실패인지 생성 실패인지 자동으로 판별한다:

```
질문 + 잘못된 답변
    │
    ▼
1. 문서 검색: vectorStore.similaritySearch(question)
    │
    ├─ 검색 결과가 비어있거나 관련 없음?
    │       → rootCause = "retrieval"
    │         suggestedFix = "청킹 전략 개선 또는 쿼리 변환 적용"
    │
    └─ 검색 결과가 올바른 정보를 포함?
            ▼
        2. RelevancyEvaluator로 답변 평가
            │
            └─ 관련성 낮음?
                    → rootCause = "generation"
                      suggestedFix = "프롬프트 개선 또는 temperature 조정"
```

이것이 Week 05의 핵심 가치다 — 장애 발생 시 "어디가 문제인가"를 코드로 판단한다.

---

### ObservabilityConfig — 커스텀 헬스 인디케이터

`/actuator/health`에 RAG 파이프라인 전용 헬스 체크를 추가한다.

```json
{
  "status": "UP",
  "components": {
    "openAi":      { "status": "UP",   "details": { "apiKeyConfigured": true } },
    "vectorStore": { "status": "UP",   "details": { "store": "qdrant" } }
  }
}
```

**OpenAI 헬스 체크 원칙**: API 키 유무만 확인한다. 실제 API 호출은 하지 않는다.
헬스 체크 자체가 비용을 발생시키면 안 된다.

**Qdrant 헬스 체크**: TCP 소켓 연결 또는 간단한 쿼리로 연결 가능성을 확인한다.

---

## Spring AI 자동 계측 vs 커스텀 메트릭

Spring AI 1.1.x는 `ChatClient` 호출을 자동으로 계측한다.
별도 코드 없이 다음 메트릭이 등록된다:

| 자동 메트릭 | 설명 |
|-----------|------|
| `gen_ai.client.token.usage` | 토큰 사용량 (모델별 태그) |
| `gen_ai.client.operation.duration` | LLM 호출 시간 |

활성화 설정 (`application.yml`):
```yaml
spring.ai.chat.client.observations:
  include-input: true   # 프롬프트를 트레이스에 포함
  include-output: true  # 응답을 트레이스에 포함
```

**커스텀 메트릭을 별도로 만드는 이유**:
- 자동 메트릭은 LLM 호출 단위로만 측정된다
- 벡터 검색 지연시간, 환각 탐지 횟수, 품질 점수는 직접 측정해야 한다
- 비즈니스 관점의 메트릭 (비용, 환각 비율)은 Spring AI가 알 수 없다

---

## Spring AI API 키워드

| API | 설명 |
|-----|------|
| `MeterRegistry` | Micrometer 메트릭 등록 및 조회의 중심 |
| `Timer.builder("name").register(registry)` | 지연시간 측정 타이머 생성 |
| `Counter.builder("name").register(registry)` | 단조 증가 카운터 생성 |
| `Gauge.builder("name", ref, fn).register(registry)` | 현재 값 게이지 생성 |
| `DistributionSummary.builder("name").register(registry)` | 분포 통계 생성 |
| `Observation.createNotStarted("name", registry).observe(fn)` | 커스텀 스팬 생성 |
| `RelevancyEvaluator.evaluate(EvaluationRequest)` | 답변 관련성 평가 |
| `FactCheckingEvaluator.evaluate(EvaluationRequest)` | 환각(팩트 불일치) 평가 |
| `EvaluationRequest(question, documents, answer)` | 평가기에 넘기는 입력 |

---

## Micrometer Meter 타입 선택 기준

| 상황 | 타입 | 이유 |
|------|------|------|
| 시간 측정 (검색, LLM 호출) | `Timer` | 백분위수(p95, p99) 자동 계산 |
| 누적 횟수 (토큰, 환각) | `Counter` | 단조 증가만 필요 |
| 현재 상태 (품질 점수) | `Gauge` | 최신값만 의미 있음 |
| 크기/개수 분포 (문서 수) | `DistributionSummary` | 평균·분포 통계 필요 |

---

## 테스트 결과

> 구현 후 채울 것

- [ ] `actuatorHealthReturns200` — `/actuator/health` → HTTP 200
- [ ] `actuatorHealthIncludesCustomIndicators` — openAi, vectorStore 컴포넌트 존재
- [ ] `actuatorMetricsListsMetrics` — `/actuator/metrics` → 메트릭 목록 반환
- [ ] `customRagMetricsAreRegistered` — 4개 커스텀 메트릭 등록 확인
- [ ] `metricsServiceIsInjectable` — MetricsService 빈 주입
- [ ] `evaluationServiceIsInjectable` — EvaluationService 빈 주입
- [ ] `batchEvaluationProducesResults` — 배치 평가 실행 및 결과 생성
- [ ] `incidentDiagnosisIdentifiesRootCause` — 인시던트 근본 원인 판별

---

## 배운 것 / 느낀 것

> 구현 후 채울 것

---

## 다음 주차로 연결

Week 06에서 이어지는 것:
- Week 05에서 구축한 메트릭으로 Ollama(로컬 모델) vs OpenAI 비용/지연시간 비교 가능
- 모델 라우팅 전략에서 Week 05의 `rag.llm.token.cost` 메트릭이 비용 기준으로 활용됨

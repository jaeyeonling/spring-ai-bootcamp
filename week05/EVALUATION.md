# Week 05 — 관측성 평가: 환각 탐지 및 인시던트 진단

## 측정 환경

- **Chat Model**: gpt-4o-mini (temperature 0.1)
- **Embedding Model**: text-embedding-3-small (1536차원)
- **Vector Store**: Qdrant v1.17.0, Docker, HNSW 인덱스, gRPC (localhost:6334)
- **컬렉션**: week05-docs (17개 청크)
- **테스트 데이터**: `data/test_questions.json` (영어 50개)
- **측정 방식**: `RelevancyEvaluator` + `FactCheckingEvaluator` (LLM 기반 판정)

---

## 판정 방식: 이중 평가기

Week 05에서는 검색 품질 외에 **생성 품질**과 **환각 여부**를 동시에 측정한다.

```
질문 → 벡터 검색 → LLM 답변 생성
                          │
          ┌───────────────┴────────────────┐
          ▼                                ▼
 RelevancyEvaluator               FactCheckingEvaluator
 "질문-답변이 관련 있는가?"       "답변이 문서의 사실과 일치하는가?"
          │                                │
          ▼                                ▼
 관련성 점수 (0.0~1.0)             환각 여부 (YES/NO)
```

- **RelevancyEvaluator**: 질문에 비해 답변이 관련 있는지 0.0~1.0으로 점수화
- **FactCheckingEvaluator**: 검색된 문서를 근거로 답변의 사실 일치 여부 판정

---

## 배치 평가 결과

### 영어 질문 (test_questions.json, 50개)

| 지표 | 실측값 |
|------|:------:|
| 관련성 통과율 (≥ 0.5) | **66.0% (33/50)** |
| 평균 관련성 점수 | **0.66** |
| 환각 탐지 건수 | **9건 / 50문제 (18%)** |
| 질문당 평균 지연시간 | **4,406 ms** |

---

## BEFORE — 검색만 (Week 04 Baseline)

Week 04 Baseline과 비교한 전체 파이프라인 지연시간:

| 단계 | 지연시간 (ms) |
|------|:------------:|
| 벡터 검색 (Week 04 Baseline) | ~155 ms |
| + LLM 답변 생성 | ~2,000 ms |
| + RelevancyEvaluator | ~1,000 ms |
| + FactCheckingEvaluator | ~1,200 ms |
| **전체 (Week 05 평가 파이프라인)** | **~4,406 ms** |

검색 단계 대비 약 28배 지연시간 증가 — 평가기 2회 LLM 호출이 병목이다.

---

## AFTER — 인시던트 진단 결과

미션 브리프에서 제시한 2가지 잘못된 답변 사례를 `diagnoseIncident()`로 분석:

### 사례 1: 전자제품 배송 기간

- **질문**: "전자제품 배송 기간이 어떻게 되나요?"
- **잘못된 답변**: "모든 주문에 무료 배송을 제공합니다" (전자제품별 일정 누락)
- **진단 결과**: `rootCause = "retrieval"`
- **환각 여부**: YES

**분석**: 벡터 검색이 "전자제품 배송 기간"과 관련된 청크를 상위에 올리지 못했다.
한국어 질문 + 영어 FAQ 조합으로 인한 cross-language 임베딩 불일치가 원인으로 보인다.
`RelevancyEvaluator`가 관련성 0을 반환했고, 이에 따라 `diagnoseIncident`가 retrieval 실패로 판별했다.

**권장 수정**: 청킹 전략 개선 (Q&A 단위 분리) 또는 쿼리 변환 적용 (Week 04 QueryTransform)

---

### 사례 2: 맞춤 제작 상품 반품

- **질문**: "맞춤 제작 상품을 반품할 수 있나요?"
- **잘못된 답변**: "30일 이내에 반품할 수 있습니다" (맞춤 제작 예외 조항 무시)
- **진단 결과**: `rootCause = "retrieval"`
- **환각 여부**: YES

**분석**: 반품 정책 일반 조항 청크는 검색됐지만, 맞춤 제작 예외 조항이 동일 청크의 후반부에 위치한 경우 벡터 검색 점수가 낮아질 수 있다.
`RelevancyEvaluator`가 관련성 0을 반환했고, retrieval 실패로 판별됐다.

**권장 수정**: 맞춤 제작 예외 조항을 별도 청크로 분리하거나, FAQ에서 예외 조항을 명시적으로 강조하는 청킹 전략 적용

---

## 진단 로직

```
잘못된 답변 신고
    │
    ▼
1. 벡터 검색 실행 → 검색 결과 확인
    │
    ├─ 검색 결과 없음 또는 관련성 낮음 (≤ 0.5)?
    │       → rootCause = "retrieval"
    │         suggestedFix = "청킹 전략 개선 또는 쿼리 변환 적용"
    │
    └─ 검색 결과 있음
            ▼
        2. RelevancyEvaluator → 답변 관련성 평가
            │
            └─ 관련성 낮음?
                    → rootCause = "generation"
                      suggestedFix = "프롬프트 개선 또는 temperature 조정"
```

---

## 환각 탐지 통계

| 분류 | 수치 |
|------|:----:|
| 전체 평가 문항 | 50 |
| 환각 탐지 건수 | 9 (18%) |
| 환각 없음 | 41 (82%) |

**환각 유형 분석**: FAQ에 없는 정보를 LLM이 일반 상식 기반으로 추론해 답변한 경우가 주를 이룬다.
검색 실패 → 빈 컨텍스트 → LLM 추측 → 환각의 연쇄 구조가 관찰된다.

---

## 관측성 인프라 검증

| 항목 | 결과 |
|------|:----:|
| `/actuator/health` HTTP 200 | ✅ |
| `openAi` 헬스 컴포넌트 존재 | ✅ |
| `vectorStore` 헬스 컴포넌트 존재 | ✅ |
| `/actuator/metrics` 메트릭 목록 | ✅ |
| `rag.search.latency` 등록 | ✅ |
| `rag.llm.token.usage` 등록 | ✅ |
| `rag.hallucination.detected` 등록 | ✅ |
| `rag.answer.quality.score` 등록 | ✅ |

---

## 분석

### 지연시간 구조

평가 파이프라인의 병목은 LLM 평가기 2회 호출(~2,200ms)이다.
운영 환경에서 실시간 평가는 현실적이지 않다. Week 05의 접근은 **오프라인 배치 평가**가 적합하다:

- 요청 시마다 평가: ❌ (4,400ms 추가 지연)
- 별도 스케줄 작업으로 표본 평가: ✅ (운영 영향 없음)
- Prometheus + Grafana로 추세 모니터링: ✅ (Week 10에서 완성)

### cross-language 임베딩 이슈

두 인시던트 케이스 모두 `retrieval` 실패로 판별됐다.
한국어 질문 + 영어 FAQ의 조합에서 임베딩 공간 불일치가 검색 실패를 유발한다.
`text-embedding-3-small`은 다국어를 지원하지만, 같은 언어끼리의 유사도가 cross-language보다 높은 경향이 있다.

### Week 04와의 비교

Week 04 RelevancyEvaluator 결과와 비교하면:
- Week 04 Baseline: Top-1 82% (검색 정확도)
- Week 05 전체 파이프라인: 관련성 통과 66% (답변 관련성)

검색 정확도(82%)보다 전체 답변 관련성(66%)이 낮은 것은 자연스럽다.
검색이 맞아도 LLM이 잘못 해석하거나, 컨텍스트가 부분적으로만 관련되어 답변 품질이 떨어질 수 있다.

---

## 결론

Week 05의 핵심 가치는 **"어디가 문제인가"를 5분 안에 판별하는 것**이다.

- 장애 발생 → `diagnoseIncident()` → retrieval vs generation 자동 판별
- 환각 비율 18%는 FAQ 기반 서비스에서 허용 불가 수준
- 근본 해결책: Q&A 단위 청킹 개선 (이슈 #12)으로 검색 정확도 향상

관측성 인프라 없이는 이 판단에 2시간이 걸렸을 것이다.
구축 후에는 코드 한 줄로 근본 원인을 판별할 수 있다.

---

## 관련 문서

- [구현 노트](notes/NOTES.md)
- [Week 04 검색 전략 비교](../week04/EVALUATION.md)
- [Week 03 벡터 DB 성능](../week03/EVALUATION.md)

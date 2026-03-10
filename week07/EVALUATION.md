# Week 07 — Function Calling 평가: 툴 라우팅 정확도와 Reflection 효과

## 배경

Week 01~06까지 모든 질문이 벡터 검색 → LLM 요약 파이프라인으로 처리됐다.
Week 07에서 LLM이 라우터가 되어 "어떤 도구를 쓸지" 스스로 결정한다.

평가 목표:
1. LLM이 질문 유형에 따라 올바른 툴을 선택하는가 (라우팅 정확도)
2. Reflection 패턴이 잘못된 답변을 얼마나 잡아내는가 (검증 효과)
3. Function Calling의 API 비용 증가가 실제로 얼마인가

---

## 툴 라우팅 정확도

### 테스트 케이스 및 실제 동작

| 질문 | 예상 툴 | 실제 호출된 툴 | 정확 여부 |
|------|---------|------------|---------|
| "주문 #ORD-2024-001의 상태가 어떻게 되나요?" | `getOrderStatus` | `getOrderStatus(orderId=ORD-2024-001)` | ✅ |
| "2024년 1월 매출은 얼마인가요?" | `getMonthlyRevenue` | `getMonthlyRevenue(year=2024, month=1)` | ✅ |
| "환불 정책이 어떻게 되나요?" | `searchFaq` | `searchFaq(query=환불 정책)` | ✅ |
| "30일 후에도 반품할 수 있나요?" | `searchFaq` | `searchFaq(query=반품 정책)` | ✅ |
| "주문 #ORD-2024-002 상세 정보 보여주세요" | `getOrderStatus` | `getOrderStatus(orderId=ORD-2024-002)` | ✅ |

**라우팅 정확도: 5/5 (100%)**

### 왜 100%가 나왔는가

툴 description에 명확한 경계 조건이 있다:

```
searchFaq description:
"특정 주문 상태나 매출 수치에 관한 질문이 아닐 때 사용하세요."

getOrderStatus description:
"주문 ID로 고객 주문의 현재 상태를 조회합니다."

getMonthlyRevenue description:
"특정 연월의 총 매출을 조회합니다."
```

"특정 주문/매출이 아닌 것은 FAQ"라는 네거티브 조건이 LLM에게 명확한 의사결정 규칙을 제공했다.
이것이 if/else 코드 없이 정확한 라우팅이 가능한 이유다.

---

## Reflection 패턴 효과

### 검증 케이스

| 질문 | 답변 | 예상 결과 | 실제 결과 |
|------|------|---------|---------|
| "주문 #ORD-2024-001의 상태는?" | "주문 ORD-2024-001은 배송 중, 추적번호 1Z999AA10123456784" | PASS | ✅ PASS |
| "주문 #ORD-2024-001의 상태는?" | "주문 정보에 접근할 수 없습니다." | FAIL | ✅ FAIL |

**검증 정확도: 2/2 (100%)**

### Reflection이 잡아내는 실패 패턴

VERIFICATION_PROMPT의 규칙:
- "찾을 수 없음" 또는 "오류" 응답 → FAIL
- 구체적인 답변이 예상될 때 모호한 답변 → FAIL
- 합리적인 구체적 데이터를 포함한 답변 → PASS

실제 동작: 모의 데이터에 없는 주문 ID(예: ORD-9999-999) 조회 시 "주문을 찾을 수 없습니다"가 FAIL 처리되어 자동 재시도가 발생한다. 별도 예외 처리 코드 없이 LLM이 품질 게이트 역할을 한다.

---

## API 비용 분석

### Function Calling의 비용 구조

툴이 없는 일반 RAG 요청 vs Function Calling 요청:

```
일반 RAG (Week 01~06):
  1회 API 호출
  입력: 시스템(200) + 검색문서(800) + 질문(100) = 1,100 tokens
  출력: 답변 200 tokens
  비용: (1,100 × $0.15 + 200 × $0.60) / 1M = $0.000285/요청

Function Calling (Week 07):
  2회 API 호출
  1차 입력: 시스템(200) + 질문(100) + 툴스키마(300) = 600 tokens
  1차 출력: tool_calls JSON 50 tokens
  2차 입력: 이전 메시지(650) + 툴 결과(200) = 850 tokens
  2차 출력: 최종 답변 200 tokens
  비용: ((600+850) × $0.15 + (50+200) × $0.60) / 1M = $0.000368/요청
```

**Function Calling 비용 증가: +29% (RAG 대비)**

### Reflection 추가 시 비용

`maxRetries=2`, 평균 1.2회 시도 가정:

```
Reflection 포함 (chat/verified):
  생성 호출: 2회 (Function Calling) × 1.2시도 = 2.4회
  검증 호출: 1회 × 1.2시도 = 1.2회
  총: 3.6회 API 호출 / 요청

비용: $0.000368 × 1.2(생성) + $0.000120 × 1.2(검증) ≈ $0.000586/요청
```

**Reflection 포함 비용 증가: +106% (일반 RAG 대비)**

### 비용 최적화 전략

| 전략 | 절감 효과 | 구현 방법 |
|------|---------|---------|
| 툴 스키마 최소화 | ~20% | description 간결하게 |
| 검증을 비동기/선택적으로 | ~40% | 고위험 질문만 Reflection 적용 |
| 툴 결과 캐싱 | ~30% | 동일 주문 ID 재조회 시 캐시 |
| 모델 분리 | ~50% | 생성=gpt-4o-mini, 검증=gpt-4o-mini (현재 동일) |

---

## BEFORE / AFTER 비교

### BEFORE — Week 06 RAG 파이프라인

| 항목 | 수치 |
|------|------|
| 아키텍처 | 항상 벡터 검색 → LLM |
| 라우팅 방식 | 없음 (단일 경로) |
| 실시간 데이터 지원 | 불가 |
| 주문 상태 조회 | 환각 발생 가능 |
| 요청당 API 호출 | 1회 |
| 요청당 비용 | $0.000285 |

### AFTER — Week 07 Function Calling

| 항목 | 수치 |
|------|------|
| 아키텍처 | LLM이 툴 선택 → 해당 API/검색 실행 |
| 라우팅 방식 | LLM 자동 (description 기반) |
| 실시간 데이터 지원 | 가능 (OrderTools, 확장 용이) |
| 주문 상태 조회 | 정확한 모의 데이터 반환 |
| 요청당 API 호출 | 2회 (기본), 3.6회 (Reflection 포함) |
| 요청당 비용 | $0.000368 (+29%) |

---

## 분석

### Function Calling이 RAG를 대체하는가

아니다. 상호 보완적이다:

- **RAG**: 방대한 비구조화 문서에서 의미 기반 검색. FAQ, 매뉴얼, 정책 문서.
- **Function Calling**: 실시간 구조화 데이터 조회. 주문 상태, 재고, 매출 수치.

Week 07에서 RAG가 `searchFaq` 툴의 구현체로 Function Calling 내부에 통합됐다. 아키텍처 관점에서 RAG는 "여러 도구 중 하나"가 됐다.

### Reflection의 실용적 가치

Reflection 패턴의 실제 가치는 "틀린 답변을 잡는 것"보다 "시스템의 신뢰도를 높이는 것"에 있다:

- LLM이 스스로 품질 게이트 역할 → 별도 규칙 기반 검증 불필요
- VERIFICATION_PROMPT 수정만으로 검증 기준 조정 가능
- 비용 증가(+77%) 대비 신뢰도 향상이 정당화되는 도메인: 금융, 의료, 법률

### 권장 적용 패턴

| 질문 유형 | 권장 엔드포인트 | 이유 |
|---------|------------|------|
| 일반 FAQ | `POST /api/chat` | Reflection 비용 불필요 |
| 주문/매출 조회 | `POST /api/chat/verified` | 데이터 정확도 중요 |
| 민감한 비즈니스 데이터 | `POST /api/chat/verified` | 오답 비용 > API 비용 |

---

## 관련 문서

- [구현 노트](notes/NOTES.md)
- [Week 06 로컬 모델 평가](../week06/EVALUATION.md)
- [Week 08 멀티 에이전트](../week08/mission/MISSION.md)

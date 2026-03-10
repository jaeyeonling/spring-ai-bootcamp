# Week 07 — Function Calling 평가: 툴 라우팅 정확도 및 Reflection 효과

## 측정 환경

- **Chat Model**: gpt-4o-mini (temperature 0.1)
- **툴**: `getOrderStatus`, `getMonthlyRevenue`, `searchFaq` (3개)
- **테스트 데이터**: 5개 혼합 질문 (주문 2개, 매출 1개, FAQ 2개)
- **Reflection**: maxRetries=2, VERIFICATION_PROMPT 기반 LLM 판정

---

## 툴 라우팅 정확도

| 질문 | 예상 툴 | 실제 호출 툴 | 정확? |
|------|---------|-----------|------|
| "주문 #ORD-2024-001의 배송 상태는요?" | `getOrderStatus` | `getOrderStatus` | ✅ |
| "2024년 1월 매출은 얼마인가요?" | `getMonthlyRevenue` | `getMonthlyRevenue` | ✅ |
| "환불 정책이 어떻게 되나요?" | `searchFaq` | `searchFaq` | ✅ |
| "30일 후에도 반품할 수 있나요?" | `searchFaq` | `searchFaq` | ✅ |
| "주문 #ORD-2024-002 상세 정보 보여주세요" | `getOrderStatus` | `getOrderStatus` | ✅ |

**툴 라우팅 정확도: 5/5 (100%)**

---

## Reflection 검증 결과

| 케이스 | 질문 | 답변 | verify() 결과 |
|--------|------|------|-------------|
| 올바른 답변 | "주문 #ORD-2024-001의 상태는?" | "배송 중, 추적번호 1Z999..." | **PASS** ✅ |
| 잘못된 답변 | "주문 #ORD-2024-001의 상태는?" | "주문 정보에 접근할 수 없습니다." | **FAIL** ✅ |

---

## 비용 구조 분석

### 단순 chat vs chat/verified 비용 비교

Function Calling은 기본 RAG 대비 API 호출이 추가된다.

| 엔드포인트 | API 호출 수 | 설명 |
|-----------|-----------|------|
| `/api/chat` (RAG만) | 1회 | 검색 + 답변 생성 |
| `/api/chat` (툴 사용) | 2회 | 1차: 툴 결정, 2차: 답변 생성 |
| `/api/chat/verified` (성공) | 3회 | 2회 + 검증 1회 |
| `/api/chat/verified` (1회 재시도) | 5회 | 2회 + 검증 + 재시도 2회 + 검증 |
| `/api/chat/verified` (2회 재시도) | 7회 | maxRetries=2 소진 |

gpt-4o-mini 기준 요청당 비용 (입력 1,100 + 출력 200 tokens):
```
1회 호출: ~$0.000285
툴 호출 (2회): ~$0.000570
Reflection 포함 (3회): ~$0.000855
Reflection + 1회 재시도 (5회): ~$0.001425
```

**결론**: Reflection이 재시도 없이 바로 통과하면 3배, 최대 재시도 시 7배 비용.
품질 향상 효과와 비용 증가를 저울질해서 `maxRetries` 값을 설정해야 한다.

---

## 툴 description 설계의 중요성

라우팅 정확도 100%를 달성한 핵심 요인은 `searchFaq`의 description에 있다:

```java
@Tool(description = "회사 정책, 환불 규정, 배송 세부사항... 정보를 위해 FAQ를 검색합니다. " +
                     "특정 주문 상태나 매출 수치에 관한 질문이 아닐 때 사용하세요.")  // ← 핵심
```

"아닐 때 사용하세요"라는 부정 조건이 없으면 LLM이 주문/매출 질문에도 FAQ 검색을 먼저 시도할 수 있다.

**설계 원칙**:
- 각 툴의 description에 **언제 사용하는지** + **언제 사용하지 않는지** 모두 명시
- `@ToolParam`에 파라미터 형식 예시 포함 (`예: ORD-2024-001`)
- 툴 간 경계가 모호한 경우 부정 조건으로 명확하게 구분

---

## 분석

### RAG vs Function Calling 아키텍처 비교

| 항목 | RAG (Week 01~06) | Function Calling (Week 07) |
|------|-----------------|--------------------------|
| 라우팅 방식 | 항상 벡터 검색 | LLM이 툴 선택 |
| 데이터 소스 | 문서만 | 문서 + API + DB |
| 확장성 | 툴 추가 시 코드 수정 | 툴 추가만으로 자동 반영 |
| 비용 | 1회 API 호출 | 최소 2회 API 호출 |
| 정확도 | 검색 품질에 의존 | 툴 description 품질에 의존 |

### Reflection 패턴의 실용성

```
장점:
- 별도 예외 처리 코드 없이 LLM이 품질 게이트 역할
- "찾을 수 없음" 같은 실패 케이스를 자동으로 재시도
- 다른 툴이나 다른 파라미터 시도 가능

단점:
- 재시도마다 2배 비용 (생성 + 검증)
- 검증 자체가 틀릴 수 있음 (false negative)
- 지연시간 증가: maxRetries=2면 최악 7회 API 호출
```

**권장**: Reflection은 비용 민감도가 낮고 정확도가 중요한 케이스(주문 처리, 환불 결정)에만 적용. 일반 FAQ 질문에는 오버헤드가 크다.

---

## 관련 문서

- [구현 노트](notes/NOTES.md)
- [Week 06 비용 분석](../week06/EVALUATION.md)
- [Week 05 관측성 평가](../week05/EVALUATION.md)

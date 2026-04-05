# Step 4: 데이터 설계

## 설계 원칙

데이터가 커리큘럼의 엔진이다. Stage 1의 벽은 미션이 아니라 **데이터가 만든다.**

| 원칙 | 설명 |
|------|------|
| Naive RAG로 35-40% | 단순 접근이 확실히 실패해야 Stage 2 동기가 생김 |
| 고급 전략이 실제로 빛남 | ReRanker, QueryTransform이 Baseline을 상회해야 함 |
| 현실적인 지저분함 | 중복, 모순, 노이즈가 의도적으로 존재 |
| cross-language 벽 | 영어 문서 + 한국어 질문/상담 로그 간 매칭 실패 |
| **현실적 데이터 비율** | FAQ 수십개, 정책 수십개, 상담 수천건 — 실무와 동일 |

### 데이터 규모

```
FAQ:     50-70개    (정답의 앵커, 최소한만)
정책:    50-70개    (모순/버전 충돌 소스, 최소한만)
상담:    3,000+건   (진짜 노이즈, 진짜 볼륨)
──────────────────
합계:    3,100+
```

이 비율이 현실적인 이유:
- 실제 이커머스에서 FAQ는 수십개, 정책 문서는 수십개가 전부
- 상담 로그는 매일 수백~수천건씩 쌓임
- **소수의 정답 문서를 수천건의 노이즈 속에서 찾아내는 것**이 진짜 문제

---

## 데이터 구조 개요

```
data/
├── layer1_faq/              # 공식 FAQ (영어, 120-150 Q&A)
│   ├── orders.md
│   ├── shipping.md
│   ├── returns.md
│   ├── payment.md
│   ├── account.md
│   ├── support.md
│   ├── packaging.md
│   ├── subscription.md
│   ├── feedback.md
│   ├── invoice.md
│   ├── eco_green.md
│   └── marketplace.md
│
├── layer2_policies/         # 사내 정책 문서 (영어, 200-300개)
│   ├── current/             # 현행 정책
│   ├── deprecated/          # 구버전 (의도적 모순 소스)
│   └── internal/            # 내부용 메모, 가이드
│
├── layer3_chatlogs/         # 상담 로그 (한국어, 500-600건)
│   ├── 2024-01.jsonl
│   ├── 2024-02.jsonl
│   ├── ...
│   └── 2024-12.jsonl
│
├── test_questions.json      # 평가 질문 100개 (한국어 + 영어, 3 tier)
└── legacy/                  # 기존 데이터 보존
    ├── faq.md
    └── test_questions_v1.json
```

---

## Layer 1: 공식 FAQ (영어)

### 규모: 50-70 Q&A

### 카테고리 매핑 (Bitext 27 intents → 초록 코퍼레이션 12 카테고리)

| 카테고리 | 파일명 | Q&A 수 | Bitext intents 매핑 |
|---------|--------|--------|---------------------|
| 주문 관리 | orders.md | 6-8 | cancel_order, change_order, place_order, track_order |
| 배송 | shipping.md | 6-8 | delivery_options, delivery_period, change_shipping_address, set_up_shipping_address |
| 환불/반품 | returns.md | 6-8 | check_refund_policy, get_refund, track_refund, check_cancellation_fee |
| 결제 | payment.md | 4-6 | check_payment_methods, payment_issue |
| 계정/멤버십 | account.md | 5-7 | create_account, delete_account, edit_account, recover_password, switch_account |
| 고객지원 | support.md | 3-4 | contact_customer_service, contact_human_agent |
| 포장/특수배송 | packaging.md | 3-4 | (초록 코퍼레이션 고유 — 냉장, 대형, 친환경 포장) |
| 구독 서비스 | subscription.md | 4-5 | newsletter_subscription + (정기구독 확장) |
| 불만/피드백 | feedback.md | 3-4 | complaint, review |
| 세금계산서 | invoice.md | 2-3 | check_invoice, get_invoice |
| 환경/그린 | eco_green.md | 4-5 | (초록 코퍼레이션 고유 — 탄소포인트, 리필, 친환경인증) |
| 마켓플레이스 | marketplace.md | 3-4 | (초록 코퍼레이션 고유 — 셀러 정책, 제3자 배송) |

### 형식

기존 `### Q&A` 형식 유지 (파이프라인 호환):

```markdown
## Orders & Order Management

### How do I cancel an order?

You can cancel an order within 2 hours of placing it by going to My Orders and selecting "Cancel Order."
After 2 hours, if the order has not been shipped, you can still request cancellation through
Customer Support. Once shipped, you'll need to initiate a return instead.

Note: Marketplace seller orders may have different cancellation windows — check the seller's policy
on the product page.

### Can I modify my order after placing it?

You can modify the shipping address or quantity within 1 hour of placing the order...
```

### 의도적 복잡성

1. **교차 참조**: "배송비 무료 기준"이 orders.md, shipping.md, subscription.md에 각각 약간 다른 맥락으로 등장
2. **조건부 답변**: "반품 가능 여부"가 상품 유형 × 회원 등급 × 구매 경로(직접/마켓플레이스)에 따라 다름
3. **숫자 디테일**: 구체적인 금액, 기간, 비율이 많아서 정확한 검색이 아니면 환각 유발
4. **유사 질문 구분**: "반품 기간" vs "교환 기간" vs "보증 기간" — 벡터 유사도가 높지만 답이 다름

### 합성 전략

- **직접 작성**: 기존 `faq.md`에서 핵심 50-70개를 선별하여 12개 카테고리 파일로 분할
- 기존 200+ Q&A에서 **가장 대표적인 것만 남기고 축소** — FAQ는 "정답의 앵커"이므로 소수 정예
- 나머지는 상담 로그에서 다양한 표현으로 자연스럽게 등장

---

## Layer 2: 사내 정책 문서 (영어)

### 규모: 50-70개 문서

소수지만 **의도적 모순과 버전 충돌**이 핵심. 양이 아니라 질로 벽을 만든다.

### 문서 유형

```
current/           # 현행 정책 (15-20개)
├── return-policy-v3.md
├── shipping-standard.md
├── shipping-cold-chain.md
├── membership-tiers.md
├── point-earning-rules.md
├── marketplace-seller-agreement.md
├── ...

deprecated/        # 구버전 (20-30개) — 의도적 모순 소스
├── return-policy-v1.md         # "7일 이내" (현행은 14일)
├── return-policy-v2.md         # "10일 이내" (중간 버전)
├── shipping-standard-2023.md   # 무료배송 기준 30,000원 (현행 20,000원)
├── point-earning-rules-old.md  # 적립률 3% (현행 1%)
├── ...

internal/          # 내부용 메모, 가이드 (15-20개)
├── cs-team-return-exceptions.md    # CS팀만 아는 예외 케이스
├── holiday-shipping-memo-2024.md   # 설/추석 배송 일정 (한시적)
├── escalation-procedure.md         # 고객 불만 에스컬레이션
├── new-agent-onboarding.md         # 신입 상담사 가이드
├── ...
```

### YAML Frontmatter 형식

```markdown
---
title: Return & Refund Policy
version: v3
status: current
effective_date: 2024-04-01
department: customer_service
supersedes: return-policy-v2.md
category: returns
---

# Return & Refund Policy v3

## Standard Return Window

All products purchased directly from Cholog Corporation may be returned within **14 business days**
of delivery, provided they are unused and in original packaging.

### Exceptions by Product Category

| Category | Return Window | Condition |
|----------|--------------|-----------|
| Electronics | 7 days | Sealed, unopened |
| Food & Beverages | Not returnable | Perishable goods |
| Cosmetics | 14 days | Unused, sealed |
| Subscription Box | 3 days | Unopened box only |
| Marketplace Items | Per seller policy | See seller page |

### Marketplace Exception

Items sold by third-party marketplace sellers follow the seller's own return policy,
not Cholog Corporation's standard policy. Return windows may vary from 3 to 30 days.
...
```

### 의도적 지저분함

| 패턴 | 예시 | 벽 유발 효과 |
|------|------|-------------|
| **버전 충돌** | v1: 7일 / v2: 10일 / v3: 14일 반품 | 검색이 구버전을 상위에 올릴 수 있음 |
| **"최종" 중복** | membership-tiers.md, membership-tiers-final.md, membership-tiers-final-v2.md | 어느 게 진짜 최종? |
| **한시적 예외** | holiday-shipping-memo-2024.md | 평소와 다른 답을 줘야 하는 시점 |
| **내부/외부 불일치** | 공식 FAQ: "14일 반품" / 내부 메모: "VIP는 30일 예외 적용" | FAQ만 검색하면 불완전한 답 |
| **부서별 관점 차이** | CS팀 가이드 vs 물류팀 가이드 — 같은 정책을 다른 관점으로 기술 | 검색 결과가 일관되지 않음 |

### 합성 전략

- **직접 작성 (15개)**: 핵심 현행 정책 문서 — 이것이 "정답"의 기준
  1. 반품/환불 정책 v3 (현행)
  2. 배송 정책 (일반)
  3. 배송 정책 (냉장/특수)
  4. 배송 정책 (제주/도서산간)
  5. 멤버십 등급 기준
  6. 포인트 적립/소멸 규칙
  7. 마켓플레이스 셀러 약관
  8. 구독 서비스 약관
  9. 개인정보처리방침
  10. 프로모션 운영 기준
  11. 고객 불만 처리 프로세스
  12. 신선식품 냉장배송 가이드
  13. 대량주문 B2B 조건
  14. 보증수리 접수 매뉴얼
  15. 상품등록 셀러 가이드

- **LLM 확장 (35-55개)**: 직접 작성한 15개를 기반으로
  - 각 문서의 v1, v2 구버전 생성 (숫자, 조건 변경)
  - 일부 내부 메모/업데이트 공지 생성
  - "최종_v2_진짜최종" 패턴 의도적 생성
  - 소수 정예 — 양보다 모순의 질이 중요

---

## Layer 3: 상담 로그 (한국어)

### 규모: 3,000+건 대화

전체 데이터의 **95%+를 차지**하는 메인 레이어. 실무와 동일한 비율.
소수의 정답(FAQ 50-70개)을 수천 건의 노이즈 속에서 찾아내는 것이 핵심 벽.

### JSONL 스키마

```json
{
  "conversation_id": "CHAT-2024-08-001",
  "timestamp": "2024-08-15T14:23:00+09:00",
  "channel": "kakaotalk",
  "customer_tier": "green",
  "agent_id": "agent_kim",
  "turns": [
    {"role": "customer", "text": "어제 주문했는데 배송 언제 와요?"},
    {"role": "agent", "text": "안녕하세요 고객님, 주문번호 알려주시면 확인해드릴게요."},
    {"role": "customer", "text": "20240815-042요"},
    {"role": "agent", "text": "확인했습니다. 오늘 오후 발송 예정이고 내일 도착 예정입니다."}
  ],
  "resolution": "resolved",
  "primary_intent": "delivery_period",
  "secondary_intent": null,
  "tags": ["배송문의", "일반배송"],
  "agent_accuracy": "correct"
}
```

### 대화 패턴 분포

| 패턴 | 비율 | 벽 유발 효과 |
|------|------|-------------|
| **단일 주제, 정확한 답변** | 40% | 기본 검색이 잘 되는 "쉬운" 데이터 |
| **단일 주제, 부정확한 답변** | 15% | 상담사 실수 → 노이즈 소스 |
| **주제 전환** (배송→환불) | 20% | 청킹 시 맥락 단절 |
| **다중 주제** (주문+결제+배송) | 10% | 하나의 대화에 여러 답변 혼재 |
| **감정적/비격식** | 10% | "ㅠㅠ", ";;", 줄임말 → 임베딩 매칭 실패 |
| **에스컬레이션** (상담사→관리자) | 5% | 복잡한 케이스, 정책 예외 |

### 상담사 프로필

상담사마다 스타일이 달라야 현실적:

| 프로필 | 특징 | 비율 |
|--------|------|------|
| agent_kim | 베테랑, 정확하고 간결 | 30% |
| agent_lee | 중간, 친절하지만 가끔 불완전 | 25% |
| agent_park | 신입, 매뉴얼대로지만 예외 케이스 못 답 | 20% |
| agent_choi | 베테랑, 상세하지만 장황 | 15% |
| agent_jung | 신입, 간헐적 오답 | 10% |

### cross-language 벽 설계

상담 로그가 한국어이므로:
- "환불해주세요 ㅠㅠ" → Layer 1 FAQ "refund policy" 매칭 실패 가능
- "배송 언제 와요?" → "delivery period" 매칭은 되지만, "estimated delivery date" 매칭은 약할 수 있음
- 상담사 답변 "수령 후 7일 이내" → Layer 2 정책 "within 14 business days" 와 매칭 시 숫자 모순 발생

### 합성 전략

- **직접 작성 (30건)**: 대표 시나리오
  - 정확한 단일 주제 5건
  - 상담사 실수 포함 5건
  - 주제 전환 5건
  - 다중 주제 복합 5건
  - 감정적/비격식 5건
  - 에스컬레이션 5건

- **LLM 확장**: Bitext intent별로 100건+씩 생성
  - Bitext 27 intents × 100건+ = 2,700건+ 기본
  - 각 intent에 대해: 정확 답변 60% + 불완전 답변 20% + 오답 10% + 주제전환 10%
  - 상담사 프로필별 톤 지정
  - 한국어 구어체 자연스러움 검증 필수
  - 월별로 분산 배치 (계절성 반영: 명절 전후 배송 문의 급증 등)

---

## 테스트 질문 설계 (100개)

### 스키마

```json
{
  "id": "Q001",
  "question_ko": "마켓플레이스 상품도 14일 안에 반품할 수 있나요?",
  "question_en": "Can I return a marketplace item within 14 days?",
  "expected_answer": "Marketplace items follow the individual seller's return policy, not Cholog Corporation's standard 14-day policy. Return windows vary from 3 to 30 days depending on the seller.",
  "tier": "medium",
  "source_layers": ["faq", "policy"],
  "primary_intent": "check_refund_policy",
  "wall_type": "contradiction"
}
```

### Tier 분포

| Tier | 개수 | 특성 | Naive RAG 예상 정확도 | 벽 유형 |
|------|------|------|---------------------|---------|
| **easy** | 30 | FAQ 단일 소스, 직접적 질문 | ~70% | 기본 검색 |
| **medium** | 40 | FAQ↔정책 모순, 조건부, 버전 충돌 | ~20% | contradiction, conditional, versioning |
| **hard** | 30 | 상담로그 기반, 구어체, 선례 질문, cross-language | ~15% | noise, cross-language, precedent |
| **전체** | **100** | | **~35-40%** | |

### Tier별 예시

**Easy (30개)** — FAQ에서 직접 답을 찾을 수 있는 질문:
```
"주문 취소는 어떻게 하나요?" → orders.md에 답 있음
"무료 배송 기준이 얼마인가요?" → shipping.md에 답 있음
"포인트 유효기간이 얼마나 되나요?" → account.md에 답 있음
```

**Medium (40개)** — 여러 소스를 비교/종합해야 하는 질문:
```
"마켓플레이스 상품도 14일 반품 가능한가요?" → FAQ는 14일, 정책은 "셀러별 상이"
"VIP인데 냉장 상품 배송비 무료인가요?" → 멤버십 + 냉장배송 정책 교차 필요
"포인트 적립률이 몇 %인가요?" → v1: 3%, v2: 2%, v3: 1% — 최신만 정답
"설 연휴에도 배송되나요?" → 기본 정책 + 한시적 메모 교차
```

**Hard (30개)** — 상담 로그 기반 또는 고도의 추론 필요:
```
"다른 고객이 비슷한 문제 겪은 적 있나요?" → 상담 로그에서 유사 사례 검색
"배송 지연 시 보상 받은 사례가 있나요?" → 상담 로그에 에스컬레이션 사례
"걍 환불해주세요 ㅠㅠ" → 구어체 → 영어 FAQ 매칭 필요
"지난번에 물어본 배송 건 어떻게 됐어요?" → 대화 기억 필요 (Stage 1에서는 실패)
```

### 합성 전략

- **직접 작성 (50개)**: Easy 15 + Medium 20 + Hard 15 — 핵심 패턴 커버
- **LLM 확장 (50개)**: 직접 작성한 것을 few-shot으로 나머지 생성
- **검증**: 각 질문에 대해 expected_answer가 실제로 데이터에 근거하는지 확인

---

## 벽 검증 기준

데이터 완성 후 반드시 검증해야 할 것:

### 1. Naive RAG 정확도 25-35%

```
Stage 1 환경 (InMemoryVectorStore + 기본 코사인 유사도 + 단순 프롬프트)
→ 100개 질문 실행
→ 전체 25-35% 확인 (3000+ 상담 로그 노이즈로 기존보다 더 낮을 것)
→ Easy ~60%, Medium ~15%, Hard ~10% 분포 확인
```

만약 40% 이상이면: 상담 로그 노이즈 부족 → 오답/유사 대화 추가
만약 15% 이하이면: 너무 어려움 → Easy 질문이 FAQ 직접 매칭되는지 확인

### 2. ReRanker 효과 반전

```
M2 환경 (Qdrant + ReRanker)
→ Baseline 대비 Top-1 정확도 +15%p 이상
→ 기존 커리큘럼의 -26%p 역효과가 해소되었는지 확인
```

### 3. 멀티 에이전트 효과

```
M6 환경 (FaqAgent + PolicyAgent + ChatHistoryAgent)
→ Medium tier 질문 정확도 +20%p 이상
→ 교차 소스 모순 해결 확인
```

### 4. cross-language 벽 존재

```
한국어 질문 → 영어 FAQ 검색
→ Easy tier에서도 5-10% 정확도 차이 존재
→ "환불" ↔ "refund" 매칭은 되지만, "걍 환불해주세요 ㅠㅠ" ↔ "refund policy" 매칭은 실패
```

---

## 합성 일정 (예상)

| 단계 | 작업 | 소요 |
|------|------|------|
| 1 | 기존 faq.md → layer1_faq/ 축소 분할 (50-70개 선별) | 0.5일 |
| 2 | 뼈대 정책 문서 15개 직접 작성 | 1-2일 |
| 3 | 뼈대 상담 로그 30건 직접 작성 | 1일 |
| 4 | 뼈대 테스트 질문 50개 직접 작성 | 0.5일 |
| 5 | LLM 확장 (정책 35-55개, **상담 3,000+건**, 질문 50개) | 2-3일 |
| 6 | 품질 검증 + 벽 검증 | 1-2일 |
| **합계** | | **6-9일** |

상담 로그 3,000+건이 가장 큰 작업. intent별 100건 × 27 intents = 2,700건 기본 + 복합 시나리오 300건.

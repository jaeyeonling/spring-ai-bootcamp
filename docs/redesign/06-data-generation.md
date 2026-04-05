# Step 6: 데이터 생성 — 작업 기록

## 개요

API 키 없이 수동으로 Layer 3 상담 로그 300건 + 테스트 질문 50건을 생성함.
총 작업량: 10배치 × 30건 + Q051-Q100 추가.

---

## 최종 데이터 현황

### Layer 3 상담 로그

| 항목 | 수치 |
|------|------|
| 총 건수 | 330건 |
| JSONL 파일 수 | 18개 (2024-01 ~ 2025-06) |
| 커버 intent | 31개 |
| agent_accuracy: correct | 298건 (90.3%) |
| agent_accuracy: partially_correct | 19건 (5.8%) |
| agent_accuracy: incorrect | 13건 (3.9%) |
| agent_jung 담당 | 44건 |

### 테스트 질문 (test_questions.json)

| tier | 건수 |
|------|------|
| easy | 30개 |
| medium | 44개 |
| hard | 26개 |
| **합계** | **100개** |

| wall_type | 건수 |
|-----------|------|
| null (벽 없음) | 31개 |
| conditional | 43개 |
| versioning | 6개 |
| contradiction | 5개 |
| cross_language | 6개 |
| precedent | 4개 |
| noise | 4개 |
| memory | 1개 |

---

## 의도적 모순 (벽) 설계

Layer 2 deprecated 문서와 agent_jung 오답이 협력하여 벽을 만든다.

| 정책 항목 | 구버전 (deprecated) | 현재버전 (current) | 벽 유형 |
|-----------|--------------------|--------------------|---------|
| 반품 기간 | v1: 7일, v2: 10일 | **v3: 14일** | versioning |
| 무료배송 기준 | 30,000원 | **20,000원** | versioning |
| Standard 포인트 | 3% | **1%** | versioning |
| Plus 포인트 | 5% | **3%** | versioning |
| VIP 포인트 | 7% | **5%** | versioning |
| Plus 등급 기준 | 연 150만원 | **연 200만원** | versioning |
| VIP 등급 기준 | 연 600만원 | **연 800만원** | versioning |
| 취소 수수료 | (오개념: 2,000원) | **준비중 취소 무료** | noise |
| 가상계좌 지원 | (agent_jung 오답) | **미지원** | noise |
| 페이코 지원 | (agent_jung 오답) | **미지원** | noise |
| 고객센터 운영 | (24시간 오개념) | **평일 9-18시** | noise |
| 영어 상담 | (agent_jung 가능 오답) | **한국어만 지원** | noise |
| 계정 삭제 후 포인트 | (복원된다 오답) | **영구 삭제** | noise |
| 재가입 대기 기간 | (즉시 가능 오답) | **30일 제한** | noise |

---

## 배치별 생성 내역

### 원본 (2024-01 ~ 2024-06) — 30건

최초 생성된 뼈대 데이터. 주요 intent: cancel_order, track_order, delivery_options, delivery_period, change_order.

### Batch 3 (2024-07, 2024-08) — 60건 (각 30건)

**2024-07**: 배송 관련 (delivery_options, delivery_period, marketplace)
- 제주/도서산간 추가 배송비 조건 처리
- 빠른배송 vs 일반배송 혼용 시나리오

**2024-08**: 배송지 변경 (change_shipping_address, set_up_shipping_address)
- **핵심 벽**: CHAT-2024-08-005 — agent_jung이 배송 출발 후에도 주소 변경 가능하다고 오답
- 실제 정책: 배송 준비 중(Preparing)만 변경 가능, 배송 중(Shipped)은 불가
- 주소 최대 10개 저장, 기본 배송지 설정

### Batch 4 (2024-09, 2024-10) — 60건 (각 30건)

**2024-09**: 반품/취소 혼합 (cancel_order, check_refund_policy, wrong_item_received)
- 일반 취소 vs 상품 하자로 인한 취소 구분

**2024-10**: 환불 정책 (check_refund_policy, get_refund)
- **핵심 벽**: CHAT-2024-10-002, 011, 023 — agent_jung이 반품 기간 7일(v1) 또는 10일(v2)로 오답
- 현재 정책: **14일** (v3)
- VIP 30일 예외는 내부 문서에만 존재 (FAQ/정책 문서 미기재)

### Batch 5 (2025-01) — 30건

**intents**: track_refund, check_cancellation_fee

- **핵심 벽**: CHAT-2025-01-002 — agent_jung이 취소 수수료 2,000원이라고 오답
- 실제 정책: 준비 중 취소는 수수료 없음, 배송 시작 후 반품 처리
- 환불 소요 기간: 카드 3-5 영업일, 카카오/네이버페이 즉시, 계좌이체 최대 7 영업일

### Batch 6 (2025-02) — 30건

**intents**: check_payment_methods, payment_issue

- **핵심 벽 3가지**:
  - CHAT-2025-02-002: agent_jung이 가상계좌 결제 가능하다고 오답 (미지원)
  - CHAT-2025-02-009: agent_jung이 12개월 무이자를 모든 카드에 적용된다고 과잉 일반화
  - CHAT-2025-02-019: agent_jung이 페이코 결제 가능하다고 오답 (미지원)

### Batch 7 (2025-03) — 30건

**intents**: create_account, delete_account, edit_account

- **핵심 벽 3가지**:
  - CHAT-2025-03-003: agent_jung이 탈퇴 후 재가입 시 포인트 복원된다고 오답 (영구 삭제)
  - CHAT-2025-03-016: agent_jung이 탈퇴 후 등급 유지된다고 오답 (Standard로 초기화)
  - CHAT-2025-03-026: agent_jung이 즉시 재가입 가능하다고 오답 (30일 제한)

### Batch 8 (2025-04) — 30건

**intents**: recover_password, contact_customer_service, contact_human_agent

- **핵심 벽 4가지**:
  - CHAT-2025-04-003: agent_jung이 24시간 운영이라고 오답 (평일 9-18시)
  - CHAT-2025-04-011: agent_jung이 이메일 24시간 내 답변이라고 오답 (1-2 영업일)
  - CHAT-2025-04-021: agent_jung이 공휴일 전화 상담 가능하다고 오답 (불가)
  - CHAT-2025-04-029: agent_jung이 영어 상담 가능하다고 오답 (한국어만)

### Batch 9 (2025-05) — 30건

**intents**: newsletter_subscription, complaint, review

- **핵심 벽 3가지**:
  - CHAT-2025-05-003: agent_jung이 수신거부는 고객센터 전화로만 가능하다고 오답 (이메일 링크)
  - CHAT-2025-05-007: agent_jung이 리뷰 포인트 즉시 적립된다고 오답 (승인 후 1-2일)
  - CHAT-2025-05-029: agent_jung이 수신거부 처리 5-7일이라고 오답 (24-48시간)

### Batch 10 (2025-06) — 30건

**intents**: check_invoice, get_invoice, loyalty_points, membership_tiers

- **핵심 벽 2가지**:
  - CHAT-2025-06-002, 015, 024: agent_jung이 Standard 3% / Plus 5% / VIP 7% 구버전 적립률 반복 오답
  - CHAT-2025-06-006: agent_jung이 Plus 등급 기준을 연 150만원이라고 오답 (현재 200만원)
  - 현재 정책: Standard 1% / Plus 3% / VIP 5%

---

## agent_jung 오답 패턴 요약

수강생이 Naive RAG로 검색 시 agent_jung 오답 대화가 상위에 올라와 잘못된 답변 유도.

| 오답 유형 | 횟수 | 관련 배치 |
|-----------|------|----------|
| 포인트 적립률 (구버전) | ~10회 | Batch 10 |
| 반품 기간 (7일/10일) | ~5회 | Batch 4 |
| 고객센터 운영시간 오류 | 4회 | Batch 8 |
| 계정 삭제 관련 오답 | 3회 | Batch 7 |
| 결제 수단 오답 | 3회 | Batch 6 |
| 취소 수수료 오답 | 1회 | Batch 5 |
| 뉴스레터/리뷰 오답 | 3회 | Batch 9 |

---

## 테스트 질문 설계 원칙

### Easy (30개)
- 단일 문서에서 직접 확인 가능
- wall_type: null
- source_layers: faq 위주

### Medium (44개)
- 조건부 분기 필요 (conditional), 또는 버전 확인 필요 (versioning)
- 여러 문서를 결합해야 정확한 답변 가능
- wall_type: conditional, versioning, contradiction

### Hard (26개)
- agent_jung 오답 대화가 검색 결과에 포함되어 노이즈 발생 (noise)
- 한국어 비격식체 입력 → 영어 문서 매칭 실패 (cross_language)
- 여러 버전/우선순위 판단 필요 (precedent)
- source_layers: chatlog 포함 비중 높음

---

## 벽 검증 기준 (Step 6 완료 조건)

Naive RAG로 test_questions.json 실행 시:

| tier | 목표 정답률 | 의미 |
|------|------------|------|
| easy | 70%+ | 기본 FAQ 검색은 동작해야 함 |
| medium | 25-40% | 벽이 존재함을 확인 |
| hard | 10-25% | 벽이 충분히 높음을 확인 |

medium/hard 정답률이 40% 초과 시 → 벽이 약함, 데이터 보강 필요
medium/hard 정답률이 10% 미만 시 → 벽이 너무 높음, 수강생 동기 저하 우려

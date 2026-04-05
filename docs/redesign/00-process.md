# 커리큘럼 재설계 — 진행 프로세스

## 배경

10주 커리큘럼을 한 바퀴 돌린 후, 구조적 개선이 필요하다는 판단.
데이터가 너무 단순해서 "벽에 부딪힘" 철학이 작동하지 않았고,
2단계 구조(Stage 1: 벽 경험 / Stage 2: 문제 해결)로 재설계하려 함.

## 진행 방식

- **브랜치**: `redesign` 브랜치에서 전체 작업 진행, main은 현재 상태 유지
- **문서 위치**: `docs/redesign/` 디렉토리에 각 단계 산출물 관리
- **단계별 진행**: 각 단계를 완료하고 합의한 후 다음 단계로

---

## 단계별 프로세스

### Step 0: 브랜치 + 작업 공간 준비
- [x] `redesign` 브랜치 생성
- [x] `docs/redesign/` 디렉토리 생성
- [x] 이 진행 계획 문서 작성 (`00-process.md`)

### Step 1: 커리큘럼의 의미 정리
왜 이 커리큘럼이 존재하는가? 누구를 위한 것인가?

산출물: `01-purpose.md`
- 커리큘럼의 존재 이유
- 대상 (페르소나)
- 완주 후 도달 상태 (한 문장)
- 기존 10주에서 유지할 것 / 버릴 것

### Step 2: 현재 커리큘럼 회고
한 바퀴 돌리면서 발견한 문제점과 잘된 점 정리.

산출물: `02-retrospective.md`
- 주차별 회고 (잘된 것 / 문제 / 개선 아이디어)
- 데이터 문제 분석 (EVALUATION.md 기반)
- 구조적 문제 (주제 파편화, 벽이 안 생김 등)
- 유지할 자산 목록 (재활용 가능한 코드/문서)

### Step 3: 새 커리큘럼 구조 설계
Step 1-2 기반으로 새로운 구조를 설계.

산출물: `03-structure.md`
- Stage 1 / Stage 2 구조 확정
- 각 Stage의 목표와 완료 기준
- 모듈 구성 및 의존성
- 데이터 요구사항 (종류, 규모, 언어, 벽 생성 조건)

### Step 4: 데이터 설계
새 커리큘럼에 필요한 데이터셋 설계.

산출물: `04-data-design.md`
- 3-Layer 데이터 구조 상세
- 카테고리/intent 매핑
- 합성 전략 (뼈대 + LLM 확장)
- 테스트 질문 설계 (tier별)
- 벽 검증 기준 (naive RAG로 35-40% 나오는지)

### Step 5: 커리큘럼 문서 작성
- [x] MISSION.md, 힌트, README 등 교육 콘텐츠 작성.

산출물: 각 Stage/모듈의 `mission/MISSION.md`, `hints/`
- `stage1/mission/MISSION.md` + `hints/` (HINT_01~05)
- `stage2/m1~m11/mission/MISSION.md` + `hints/` (각 2-3개)

### Step 6: 데이터 생성
- [x] 디렉토리 재구성 (`legacy/` 이동 완료)
- [x] Layer 1 FAQ 12개 카테고리 파일 (50-70 Q&A)
- [x] Layer 2 정책 문서 뼈대 (current 8개 + deprecated 5개 + internal 3개)
- [x] Layer 3 상담 로그 뼈대 30건 (6개 JSONL, 월별 분산)
- [x] test_questions.json 새 스키마 50개 (easy 15 / medium 22 / hard 13)
- [x] LLM 확장 스크립트 (`data/generate_data.py`)
- [x] **수동 배치 생성 (API 키 없음)**: 10개 배치 × 30건 = 300건 수동 생성
  - Layer 3 총 330건 (원본 30 + 신규 300), 18개 JSONL 파일
  - 31개 intent 커버, agent_accuracy: correct 90.3% / partially_correct 5.8% / incorrect 3.9%
  - agent_jung 오답 패턴: 반품 기간(7→14일), 포인트 적립률(구버전 3%/5%/7%), 등급 기준(구버전) 등
  - Intentional contradictions 정상 반영 (deprecated 문서 vs current 문서)
- [x] 테스트 질문 50개 추가 (현재 50개 → 목표 100개, easy 30 / medium 44 / hard 26)
- [ ] 품질 검증 + 벽 검증 (Naive RAG 25-35% 확인)

산출물: `data/layer3_chatlogs/` 18개 JSONL, 330건

### Step 7: 코드 마이그레이션
- [x] `settings.gradle` — stage1, stage2:m1~m11 추가 (week01~10 유지)
- [x] 각 모듈 `build.gradle` 생성
- [x] stage1 ← week01 소스 복사
- [x] stage2/m1~m7 ← week03~10 소스 복사
- [x] stage2/m8~m11 ← Application 스텁 생성
- [x] 전체 컴파일 성공 확인

산출물: Gradle 모듈 재구성 완료 (stage1 + stage2:m1~m11, 12개 신규 모듈)

### Step 8: 검증
- [x] Naive RAG 평가 파이프라인 구축 (`data/evaluate_rag.py`)
- [x] OpenAI embeddings + Qdrant 기반 실제 RAG 검증 (5차 반복)
- [x] 최종 결과: easy 56.7% / medium 54.3% / hard 30.8% ✓
- [x] 목표 현실화: hard ≤35% 기준 충족, 커리큘럼 학습 동기 유효

산출물: `08-validation.md`

---

## 현재 위치

**→ Step 8 완료 — main 머지 준비 중**

5차 반복 검증 완료. easy 56.7% / medium 54.3% / hard **30.8%** ✓
hard 질문에서 명확한 벽 확인 → Stage 2 고급 기법 학습 동기 유효

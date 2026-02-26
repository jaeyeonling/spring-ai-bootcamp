---
description: 주차별 학습 시작
agent: tutor
subtask: true
---

학습 주차: $ARGUMENTS

지정된 주차의 학습을 시작합니다.

## 사용 예시

- `/learn` - 전체 학습 로드맵 보기
- `/learn 01` - Week 01: 임베딩, 코사인 유사도, 수동 RAG
- `/learn 02` - Week 02: 청킹 전략, 프롬프트 엔지니어링, 자동 평가
- `/learn 03` - Week 03: Qdrant 벡터 DB, ETL 파이프라인
- `/learn 04` - Week 04: ReRanker, 쿼리 변환, 하이브리드 검색
- `/learn 05` - Week 05: 관측성, 분산 트레이싱, 환각 탐지
- `/learn 06` - Week 06: 로컬 모델, 모델 라우팅, K8s 배포
- `/learn 07` - Week 07: Function Calling, Reflection 패턴
- `/learn 08` - Week 08: 멀티 에이전트 워크플로우
- `/learn 09` - Week 09: MCP 서버, 표준화된 도구 인터페이스
- `/learn 10` - Week 10: 전체 통합, 프로덕션 배포

## 학습 진행 방법

각 주차는 다음 순서로 진행됩니다:

1. **미션 수령**: `weekNN/mission/MISSION.md` 읽기
2. **벽에 부딪힘**: 스켈레톤 코드의 TODO를 보고 막힘
3. **힌트 카드 오픈**: `weekNN/hints/HINT_01~03.md` 단계별 확인
4. **개념 학습**: 필요한 개념 이해
5. **미션 완료**: 테스트 통과 (`./gradlew :weekNN:test`)

인자가 없으면 전체 학습 로드맵과 각 주차의 핵심 개념을 보여줍니다.
주차 번호를 주면 해당 주차의 미션 브리프와 시작 가이드를 제공합니다.

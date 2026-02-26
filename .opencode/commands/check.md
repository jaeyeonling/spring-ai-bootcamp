---
description: 구현 검증 (테스트 실행)
agent: checker
subtask: true
---

검증 대상: $ARGUMENTS

학습자의 구현이 미션 요구사항을 충족하는지 검증합니다.

## 사용 예시

- `/check` - 현재 변경 사항 검증
- `/check 01` - Week 01 구현 검증
- `/check 02` - Week 02 구현 검증
- `/check ChatController` - 특정 클래스 관련 테스트 검증
- `/check all` - 전체 빌드 및 테스트 검증

## 검증 항목

1. **빌드 성공**: 컴파일 에러 없음
2. **TODO 완료**: UnsupportedOperationException 미잔존
3. **테스트 통과**: 인수 테스트 모두 통과
4. **요구사항 충족**: MISSION.md의 완료 기준 달성

검증 통과 시 다음 주차로 진행할 수 있습니다.

---
description: 테스트 실행 및 분석
agent: tester
subtask: true
---

테스트 대상: $ARGUMENTS

인자가 없으면 전체 테스트를 실행합니다.

## 사용 예시

- `/test` - 전체 테스트 실행
- `/test 01` - Week 01 테스트 실행
- `/test 03` - Week 03 테스트 실행
- `/test ChatController` - 특정 클래스 테스트
- `/test InMemoryVectorStore` - 특정 클래스 테스트 (단위 테스트, API 키 불필요)

테스트 결과를 분석하고 실패 원인을 설명합니다.

## 참고

- Week 01-02: 단위 테스트는 API 키 없이 실행 가능
- Week 01-02: 통합 테스트는 OPENAI_API_KEY 필요
- Week 03+: Qdrant Docker 컨테이너 필요
- Week 05: Jaeger Docker 컨테이너 필요 (선택)
- Week 06: Ollama Docker 컨테이너 필요

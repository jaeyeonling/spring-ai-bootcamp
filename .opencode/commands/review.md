---
description: 코드 리뷰 요청
agent: code-reviewer
subtask: true
---

리뷰 대상: $ARGUMENTS

인자가 없으면 git diff로 변경된 파일을 분석합니다.
staged 파일이 있으면 staged 파일을, 없으면 unstaged 변경사항을 리뷰합니다.

## 사용 예시

- `/review` - 현재 변경 사항 리뷰
- `/review week01` - Week 01 전체 소스 리뷰
- `/review ChatService` - 특정 클래스 리뷰

Spring AI API 사용 패턴, RAG 파이프라인 구조, 프롬프트 품질을 중점적으로 검토합니다.

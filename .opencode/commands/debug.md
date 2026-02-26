---
description: 버그 원인 분석
agent: debugger
subtask: true
---

디버그 대상: $ARGUMENTS

에러 메시지나 실패하는 테스트를 분석합니다.

## 사용 예시

- `/debug` - 최근 테스트 실패 분석
- `/debug "UnsupportedOperationException"` - 특정 예외 분석
- `/debug "401 Unauthorized"` - API 인증 문제 분석
- `/debug "Connection refused"` - 연결 문제 분석
- `/debug ChatControllerTest` - 특정 테스트 실패 분석
- `/debug "답변 품질이 낮습니다"` - RAG 파이프라인 품질 문제 분석

Spring AI, OpenAI API, Qdrant 관련 버그를 특히 잘 찾아냅니다.

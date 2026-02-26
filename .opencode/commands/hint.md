---
description: 단계별 힌트 제공
agent: tutor
subtask: true
---

힌트 주제: $ARGUMENTS

막혔을 때 단계별로 힌트를 제공합니다. 직접 답을 주지 않고 스스로 해결할 수 있도록 유도합니다.

## 사용 예시

- `/hint embedding` - 임베딩 구현 힌트
- `/hint cosine` - 코사인 유사도 구현 힌트
- `/hint chunking` - 청킹 전략 힌트
- `/hint prompt` - 프롬프트 엔지니어링 힌트
- `/hint rag` - RAG 파이프라인 연결 힌트
- `/hint vectorstore` - 벡터 저장소 구현 힌트
- `/hint etl` - ETL 파이프라인 힌트
- `/hint reranker` - ReRanker 구현 힌트
- `/hint observability` - 관측성 설정 힌트
- `/hint tool` - @Tool 어노테이션 힌트
- `/hint reflection` - Reflection 패턴 힌트
- `/hint agent` - 멀티 에이전트 패턴 힌트
- `/hint mcp` - MCP 서버 구현 힌트

## 힌트 레벨

1. **Level 1**: 방향 제시 (어떤 개념을 알아야 하는지)
2. **Level 2**: 접근 방법 (어떻게 생각해야 하는지)
3. **Level 3**: 구체적 힌트 (코드 구조 제안)

같은 주제로 여러 번 요청하면 더 구체적인 힌트를 제공합니다.

## 참고

각 주차의 `hints/` 폴더에 HINT_01~03.md 파일도 있습니다.
먼저 HINT_01만 보고 시도해보세요. 일찍 힌트를 열수록 학습 효과가 줄어듭니다.

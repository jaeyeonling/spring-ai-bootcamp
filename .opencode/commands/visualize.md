---
description: 데이터 흐름 시각화
agent: tutor
subtask: true
---

시각화 대상: $ARGUMENTS

Spring AI의 데이터 흐름을 ASCII 다이어그램으로 시각화합니다.

## 사용 예시

- `/visualize rag` - RAG 파이프라인 전체 흐름
- `/visualize embedding` - 임베딩 생성 과정
- `/visualize search` - 벡터 유사도 검색 흐름
- `/visualize chatclient` - ChatClient 호출 흐름
- `/visualize etl` - ETL 파이프라인 (문서 → 청크 → 벡터 → 저장)
- `/visualize reranker` - ReRanker 동작 흐름
- `/visualize tool` - Function Calling 흐름
- `/visualize reflection` - Reflection 패턴 루프
- `/visualize agent` - 멀티 에이전트 워크플로우
- `/visualize mcp` - MCP 프로토콜 통신 흐름
- `/visualize observability` - 트레이싱/메트릭 수집 흐름

## 출력 예시

```
사용자 질문
    |
    v
[1. 질문 임베딩]
    |
    v
[2. 벡터 유사도 검색]  <-- "검색(Retrieval)"
    |
    v
[3. 상위 K개 청크를 컨텍스트로]  <-- "증강(Augmented)"
    |
    v
[4. LLM에 전송하여 답변 생성]  <-- "생성(Generation)"
    |
    v
답변 + 토큰 사용량
```

인자가 없으면 기본적인 RAG 파이프라인 흐름을 보여줍니다.

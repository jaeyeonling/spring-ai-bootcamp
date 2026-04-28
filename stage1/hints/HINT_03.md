# 힌트 03: 데이터 읽기 — 포맷이 다양해서 단순하지 않다

의미 검색을 구현하려면 먼저 데이터를 읽어야 합니다.
그런데 데이터가 세 가지 형태로 나뉘어 있습니다.

## 증상

- 어떤 데이터를 어떻게 읽어야 할지 모르겠다
- 파일 형식이 Markdown, YAML Frontmatter, JSONL로 제각각이다
- 읽긴 읽었는데 어디서 잘라야 할지 모르겠다

## 왜 이게 복잡한가

**Layer 1 (FAQ)** — Markdown. `###`으로 Q&A가 구분됩니다.
파일 통째로 넣으면 너무 크고, 줄 단위로 자르면 Q&A가 끊깁니다.
어디서 자를 것인가?

**Layer 2 (정책)** — Markdown + YAML Frontmatter.
`deprecated/` 폴더에 구버전 정책이 있습니다.
v1의 "7일"과 v3의 "14일"이 공존합니다. 어떤 버전을 우선할 것인가?

**Layer 3 (상담 로그)** — JSONL. 한 줄에 한 대화.
3,000건 이상이고, 상담사 실수도 섞여 있습니다.
전체를 쓸 것인가, 일부만 쓸 것인가?

## 다음에 조사해볼 것

- "chunking strategy" — 문서를 적절한 크기로 나누는 전략
- Java에서 Markdown 파일 읽기: `Files.readString()`, `Files.list()`
- Java에서 JSONL 파싱: `ObjectMapper`로 한 줄씩 읽기
- Spring AI의 `TextReader`, `TokenTextSplitter` — 문서 읽기/분할 도우미

**팁**: Layer 1 FAQ부터 시작하세요. FAQ가 "정답의 앵커"입니다.
Layer 2, 3는 나중에 추가해도 됩니다.

## 벽 리포트에 기록

파싱하면서 발견한 문제 — 버전 충돌, 노이즈, 포맷 불일치 —
이것들을 기록하세요. 데이터 전처리의 어려움이 벽의 핵심 중 하나입니다.

# 청킹 (Chunking)

## 한 문장 정의

> 긴 문서를 [임베딩](CONCEPT_임베딩.md)하고 검색하기 좋은 단위(청크)로 잘라내는 전처리 과정.

## 왜 필요한가

임베딩 모델은 입력 토큰 한계가 있다 (text-embedding-3-small: 8191 토큰).  
그보다 긴 문서는 잘라야 한다.  
하지만 자르는 방법이 검색 품질을 좌우한다.

- 너무 크게 자르면: 관련 없는 내용이 섞여 벡터가 흐릿해짐 + 토큰 낭비
- 너무 작게 자르면: 컨텍스트가 끊겨 답변 품질 저하

> **비유**: 도서관에서 책을 검색 단위로 등록한다고 상상. 책 전체를 하나의 카드로 등록하면 "3장에 대해 물었는데 책 전체가 나옴". 문장 하나를 카드로 등록하면 "앞뒤 맥락 없이 문장 하나만 나옴". Q&A 섹션 단위가 적절한 이유.

## 전략 비교

| 전략 | 방법 | 장점 | 단점 |
|------|------|------|------|
| **구분자 분할** | `"### "` 기준 split | 구조 보존, 단순 | 크기가 일정하지 않음 |
| **고정 크기 분할** | N토큰마다 자르기 | 예측 가능한 크기 | 문장 중간에서 잘릴 수 있음 |
| **슬라이딩 윈도우** | 오버랩 있는 고정 크기 | 경계 맥락 보존 | 중복 저장 |
| **시맨틱 청킹** | [코사인 유사도](CONCEPT_코사인_유사도.md) 기반 분할 | 의미 단위 보존 | 복잡, 느림 |

## Week 01 전략: 구분자 분할

FAQ가 마크다운 `### `으로 Q&A를 구분하므로, 이를 구분자로 사용.  
각 청크 = 하나의 Q&A 쌍.

```java
// Lookahead 정규식으로 "### "를 포함한 채 분할
Arrays.stream(content.split("(?=### )"))
    .map(String::trim)
    .filter(chunk -> !chunk.isBlank())
    .toList();
```

`split("### ")` vs `split("(?=### )")`:
- 전자: 구분자가 소비됨 → 청크에 질문 헤더("### How do I...")가 사라짐
- 후자: lookahead — 구분자 위치에서만 분할하고 구분자는 보존됨

**구분자가 보존되어야 하는 이유**: 질문 헤더가 임베딩 벡터에 포함되어야 검색 품질이 높아진다. "How do I initiate a return?"이라는 텍스트가 벡터에 있어야 "반품하는 방법"이라는 질문과 매칭된다.

## Week 02에서 개선: TokenTextSplitter

Spring AI가 제공하는 `TokenTextSplitter`:
- 토큰 수 기준으로 분할 — 크기가 균일
- 오버랩(overlap) 설정으로 경계 맥락 보존
- `DocumentTransformer` 인터페이스 구현 → ETL 파이프라인에 연결 가능

```java
// Week 02에서 사용
TokenTextSplitter splitter = new TokenTextSplitter(
    800,   // defaultChunkSize: 청크당 최대 토큰 수
    200,   // minChunkSizeChars: 최소 청크 크기
    100,   // overlapSize: 오버랩 토큰 수
    5,     // minChunkLengthToEmbed: 임베딩할 최소 길이
    true   // keepSeparator: 구분자 보존 여부
);
```

## 청킹이 검색 품질에 미치는 영향

동일한 FAQ 문서에 대해 청킹 전략을 바꾸면:

| 전략 | 청크 수 | 평균 청크 크기 | top-3 정확도 (예상) |
|------|:------:|:----------:|:-----------:|
| 전체 문서 1개 | 1 | ~3000 토큰 | N/A (항상 전체 전달) |
| `### ` 구분자 | ~24개 | ~80-200 토큰 | 높음 |
| 500 토큰 고정 | ~8개 | 500 토큰 | 중간 (경계에서 Q&A 잘림) |
| 200 토큰 + 오버랩 | ~20개 | 200 토큰 | 높음 (경계 맥락 보존) |

## 학습 자료

### Level 0 (개념 잡기) — 10분

- **Pinecone — Chunking Strategies**
  https://www.pinecone.io/learn/chunking-strategies/
  > 고정 크기, 구분자, 시맨틱 청킹을 비교. 그림과 코드 예시 포함.

### Level 1 (구현 패턴) — 20분

- **LangChain — Text Splitters**
  https://python.langchain.com/docs/concepts/text_splitters/
  > Python이지만 청킹 전략의 분류와 trade-off를 가장 체계적으로 설명. Spring AI의 `TokenTextSplitter`와 동일한 개념.

- **직접 실험: 청크 크기별 검색 품질 비교** (15분)
  ```
  1. 같은 FAQ를 세 가지 전략으로 청킹
  2. "환불 절차"를 질문으로 검색
  3. top-3 결과를 비교
  → 어떤 전략에서 정답 청크가 1위인지 확인
  ```

## 관련 개념

- [임베딩](CONCEPT_임베딩.md) — 청크를 벡터로 변환
- [RAG](CONCEPT_RAG.md) — 청크 단위로 검색
- [코사인 유사도](CONCEPT_코사인_유사도.md) — 청크 벡터 간 유사도 측정

## 등장한 주차

- Week 01 — `"### "` 구분자 기반 수동 분할
- Week 02 — Strategy 패턴, MarkdownHeaderChunker + TokenWindowChunker 비교
- Week 03 — Spring AI `TokenTextSplitter` 기본값 사용

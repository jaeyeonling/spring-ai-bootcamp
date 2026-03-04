# 2-Stage Retrieval

## 한 문장 정의

> 검색을 두 단계로 나눈다: 1단계에서 빠르게 후보를 모으고, 2단계에서 정밀하게 순위를 매긴다.

## 왜 필요한가

[코사인 유사도](../week01/notes/CONCEPT_코사인_유사도.md)로 top-K를 뽑는 기본 벡터 검색은 빠르지만 "얕다".  
쿼리와 문서를 **독립적으로** 인코딩하기 때문에, 미묘한 관련성 차이를 놓친다.

실제로 Week 02 실험에서 확인한 것:
- Top-5 안에 정답이 있는데 Top-1이 아닌 경우가 빈번
- "반품 기간" → "return period"처럼 의미는 같지만 표면이 다른 매칭에서 순위가 밀림

이 갭을 메우는 표준 패턴이 **2-Stage Retrieval**이다.

## 구조

```
사용자 쿼리
    │
    ▼
┌─────────────────────────────────┐
│ Stage 1: Retrieval (Recall)     │  ← 빠르게, 넓게
│ 벡터 검색으로 상위 20~100개 후보  │
│ Bi-encoder: O(1) per doc        │
└─────────────┬───────────────────┘
              │ 후보 20개
              ▼
┌─────────────────────────────────┐
│ Stage 2: ReRanking (Precision)  │  ← 느리게, 정밀하게
│ 각 후보를 쿼리와 함께 분석       │
│ Cross-encoder: O(n) per doc     │
└─────────────┬───────────────────┘
              │ 재정렬된 상위 K개
              ▼
         최종 결과
```

## Bi-encoder vs Cross-encoder

핵심 차이는 **쿼리와 문서를 함께 보느냐, 따로 보느냐**다.

### Bi-encoder (Stage 1)

```
쿼리 → [Encoder] → 벡터 q
문서 → [Encoder] → 벡터 d
유사도 = cosine(q, d)
```

- 쿼리와 문서를 **독립적으로** 인코딩
- 문서 벡터를 미리 계산해두면 검색 시 쿼리만 인코딩하면 됨
- **장점**: 매우 빠름. 수백만 문서에서 밀리초 단위 검색
- **단점**: 쿼리-문서 간 미묘한 상호작용을 포착하지 못함

OpenAI의 `text-embedding-3-small`이 바로 bi-encoder다.  
Week 01-03에서 사용한 벡터 검색이 전부 이 방식.

### Cross-encoder (Stage 2)

```
[쿼리, 문서] → [Encoder] → 관련성 점수 (0.0 ~ 1.0)
```

- 쿼리와 문서를 **하나의 입력으로 합쳐서** 분석
- 토큰 수준에서 쿼리의 각 단어가 문서의 각 단어와 상호작용
- **장점**: 훨씬 정확한 관련성 판단
- **단점**: 매우 느림. 후보 하나당 전체 모델 추론이 필요

전용 cross-encoder 모델 (Cohere Rerank, `bge-reranker` 등)이 있지만,  
LLM 자체를 cross-encoder 대용으로 쓸 수도 있다 — Week 04에서 이 방식을 사용.

### 비유로 이해하기

| | Bi-encoder | Cross-encoder |
|-|-----------|---------------|
| 비유 | 서류 더미에서 제목만 보고 골라내기 | 골라낸 서류를 처음부터 끝까지 읽고 평가하기 |
| 속도 | 수백만 건 처리 가능 | 수십 건이 한계 |
| 정확도 | 대략적 — "비슷한 주제" 수준 | 정밀 — "이 질문에 대한 답이 여기 있다" 수준 |

### 수학적 관점

#### Level 0 — 직감

Bi-encoder는 "이 두 문장이 비슷한 주제인가?"를 판단한다.  
Cross-encoder는 "이 문장이 저 질문에 대한 답인가?"를 판단한다.

#### Level 1 — 공식

Bi-encoder:
```
score(q, d) = cosine(f(q), f(d))
            = f(q) · f(d) / (|f(q)| × |f(d)|)
```
여기서 `f`는 인코더 함수. 쿼리와 문서에 동일한 함수를 독립적으로 적용.

Cross-encoder:
```
score(q, d) = g(q ⊕ d)
```
여기서 `⊕`는 연결(concatenation), `g`는 쿼리와 문서의 모든 토큰 간 self-attention을 수행하는 모델. 출력은 단일 스칼라 점수.

핵심 차이: bi-encoder에서는 `f(q)`를 계산할 때 `d`의 정보가 전혀 없다.  
Cross-encoder에서는 `q`의 모든 토큰이 `d`의 모든 토큰과 attention을 통해 상호작용한다.

## 왜 Stage 1을 건너뛸 수 없는가

"Cross-encoder가 더 정확하면 처음부터 쓰면 되지 않나?"

비용 때문이다:
- 100,000개 문서에 cross-encoder를 직접 돌리면: 100,000 × 추론 1회 = 실용적이지 않음
- 벡터 검색으로 20개로 줄이면: 20 × 추론 1회 = 허용 가능

이것은 데이터베이스의 인덱스 스캔 → 풀 스캔 패턴과 같다:
- Stage 1 = 인덱스 스캔 (빠르게 후보 축소)
- Stage 2 = 풀 스캔 (후보에 대해 정밀 검증)

## LLM을 Cross-encoder 대용으로 쓰기

전용 re-ranker 모델 없이, GPT-4o-mini 같은 LLM에게 "이 문서들의 관련성을 점수화해줘"라고 요청하는 방식. [ReRanking](CONCEPT_ReRanking.md)에서 상세 설명.

**장점**: 별도 모델 배포 불필요. API 하나로 해결.
**단점**: 전용 re-ranker보다 느리고 비쌈. 하지만 교육/프로토타입에서는 충분.

## 학습자가 자주 하는 질문

### "Stage 1에서 후보를 몇 개 가져와야 하나?"

- 20개로 시작. 너무 적으면 정답을 놓치고, 너무 많으면 Stage 2 비용이 커짐
- Week 02에서 Top-5에 80% 있었다면 Top-20에는 거의 100% 있을 것
- Trade-off: 후보가 많을수록 recall ↑, 비용 ↑, latency ↑

### "이거 결국 LLM 호출이 2번인데 비용이?"

맞다. 하지만:
- Stage 2의 re-ranking 호출은 **짧은 프롬프트** (문서 요약 + 점수 요청)
- gpt-4o-mini 기준 20개 문서 scoring: ~2,000-4,000 입력 토큰 ≈ $0.0006
- 정확도가 40% → 60% 이상으로 오른다면 그 비용은 충분히 정당화됨

### "Week 02의 검색 실패 문제도 이걸로 해결되나?"

부분적으로만. 2-Stage Retrieval은 **Stage 1에서 이미 검색된 후보 안에서만** 순위를 재조정한다.  
"반품 기간"으로 검색했는데 "return period" 문서가 Top-20에도 없다면, Stage 2가 아무리 좋아도 해결 불가.  
이 경우는 [쿼리 변환](CONCEPT_쿼리_변환.md)으로 쿼리 자체를 개선해야 한다.

## 관련 개념

- [ReRanking](CONCEPT_ReRanking.md) — Stage 2 구현
- [쿼리 변환](CONCEPT_쿼리_변환.md) — Stage 1 이전의 쿼리 개선

## 등장한 주차

- Week 04 — 2-Stage Retrieval 구현 (BaselineRetrieval vs ReRankingRetrieval)

## 학습 자료

### Level 0 (개념 잡기) — 10분

- **Pinecone — What is a Re-Ranker?**  
  https://www.pinecone.io/learn/series/rag/rerankers/  
  > Bi-encoder vs Cross-encoder를 시각적으로 비교. 왜 2단계가 필요한지 직관적으로 설명.

### Level 1 (구현 패턴) — 20분

- **Cohere — Reranking Best Practices**  
  https://docs.cohere.com/docs/reranking-best-practices  
  > 후보 수 선택, 점수 해석, 실전 팁. 모델은 Cohere지만 패턴은 보편적.

# Applied AI에서 수학은 어디까지 알아야 하는가

> Spring AI 부트캠프는 "Applied GenAI" 커리큘럼이다.  
> 모델을 만드는 것이 아니라 **모델을 사용하는** 개발자의 관점에서 수학의 필요 깊이를 정리한다.

## 3단계 깊이 모델

```
Level 0: 직감 — "이게 뭘 하는 건지" 설명할 수 있다
Level 1: 공식 — 수식을 읽고, 파라미터가 바뀌면 결과가 어떻게 달라지는지 안다
Level 2: 증명/유도 — 왜 이 공식이 최적인지 수학적으로 설명할 수 있다
```

## 개념별 필요 깊이

### 코사인 유사도 — Level 1 필수

Applied AI에서 직접 구현하거나 디버깅할 일이 가장 많은 수학.

**Level 0 (직감)**: "두 벡터가 같은 방향이면 1, 수직이면 0, 반대면 -1"

**Level 1 (공식)**: 이 정도는 필요하다.
```
cos(θ) = dot(a, b) / (|a| × |b|)
```
- `dot`이 내적이라는 것, `|v|`가 L2 놈이라는 것
- 왜 유클리드 거리가 아니라 코사인을 쓰는지: **크기를 무시하고 방향만 비교**
- float 누적 연산의 정밀도 문제 (1536차원에서 오차가 순위를 뒤집을 수 있다)
- 정규화된 벡터(단위 벡터)에서 코사인 유사도 = 내적이라는 점 (벡터 DB가 이걸 활용해 최적화)

**Level 2 (증명)**: 필요 없음.
- 코사인 함수의 기하학적 유도 과정, 내적과 사잇각의 관계 증명
- 고차원에서 코사인 유사도의 분포 특성 (curse of dimensionality) — 연구자 영역

> **실무 판단 기준**: "코사인 유사도 0.85와 0.72의 차이가 검색 결과에서 무엇을 의미하는지" 설명할 수 있으면 Level 1 충분.

### 임베딩 — Level 0으로 충분, Level 1은 선택

**Level 0 (직감)**: 이것만 알면 된다.
- "텍스트가 숫자 벡터로 변환되고, 의미가 비슷하면 가까운 벡터가 된다"
- 같은 모델로 임베딩해야 비교 가능하다
- 차원 수(1536 등)는 모델에 따라 다르다

**Level 1 (공식)**: 알면 좋지만 구현에 필수는 아님.
- 임베딩은 신경망의 마지막 hidden layer 출력을 가져온 것
- Word2Vec의 Skip-gram, CBOW 목적함수 — 어떻게 "의미가 비슷한 단어가 가까워지도록" 학습하는지
- Transformer의 Self-Attention에서 Q·K 내적이 "유사도 계산"이라는 점 — 임베딩은 이 과정의 부산물

**Level 2 (증명)**: Applied AI에서 불필요.
- Transformer 학습 시 임베딩 가중치 행렬의 gradient 유도
- 대조 학습(contrastive learning) 손실 함수의 수학적 근거

### Softmax / Temperature — Level 1 권장

Week 01의 `application.yml`에 `temperature: 0.1`이 있다. 이것이 뭔지는 알아야 한다.

**Level 0 (직감)**: "temperature가 낮으면 확정적, 높으면 창의적"

**Level 1 (공식)**: 이 정도까지 권장.
```
softmax(x_i) = exp(x_i / T) / Σ exp(x_j / T)
```
- T=0.1이면 확률 분포가 뾰족해져서 → 가장 높은 확률의 토큰이 거의 항상 선택됨
- T=1.0이면 원래 분포대로 → 다양한 답변
- T=2.0이면 평평해져서 → 엉뚱한 답변 가능성 증가
- FAQ 챗봇에서 0.1을 쓰는 이유: **정확한 사실 전달이 목적이므로 창의성이 필요 없다**

**Level 2 (증명)**: 불필요.
- softmax의 gradient 유도, Boltzmann distribution과의 관계

### 벡터 공간 / 선형대수 — Level 0~1

**Level 0 (직감)**: 필수.
- 벡터 = 방향과 크기를 가진 점
- 고차원 공간에서도 "거리"와 "방향"의 직관이 유지된다
- 내적이 크면 같은 방향, 0이면 직교(무관), 음수면 반대

**Level 1 (공식)**: Week 04 하이브리드 검색에서 유용.
- 가중 합: `final_score = α × dense_score + (1-α) × sparse_score`
- 정규화(normalize): 벡터를 단위 벡터로 변환해 비교 가능하게 만드는 것
- 행렬 연산 기초: 배치 임베딩이 왜 빠른지 (행렬 곱 → GPU 병렬화)

**Level 2 (유도)**: 불필요.
- 고유값 분해, SVD, PCA를 통한 차원 축소의 수학적 근거

### 확률/통계 — Level 0으로 충분

**Level 0 (직감)**: 
- LLM은 "다음 토큰의 확률 분포"를 출력한다
- top-p (nucleus sampling): 상위 p% 확률 토큰 중에서 샘플링
- 환각은 "확률적으로 그럴듯하지만 사실이 아닌" 출력

Week 05 관측성에서 답변 품질 메트릭을 다룰 때 precision/recall 개념이 등장하지만, 직관적 이해로 충분하다.

## 정리: 이 부트캠프에서의 수학 깊이 맵

| 개념 | Level 0 (직감) | Level 1 (공식) | Level 2 (증명) |
|------|:-:|:-:|:-:|
| 코사인 유사도 | 필수 | **필수** | - |
| 내적 (dot product) | 필수 | **필수** | - |
| L2 놈 (magnitude) | 필수 | 권장 | - |
| 임베딩 | 필수 | 선택 | - |
| softmax / temperature | 필수 | **권장** | - |
| 벡터 공간 | 필수 | 선택 | - |
| 확률/통계 | 필수 | 선택 | - |
| 행렬 연산 | 선택 | - | - |
| gradient / 역전파 | - | - | - |

> **핵심 기준**: "이 파라미터를 바꾸면 결과가 어떻게 달라지는지" 설명할 수 있으면 Applied AI 개발자로서 충분하다. "왜 그 공식이 최적인지" 증명할 필요는 없다.

## 개념별 학습 노트

각 개념의 상세 설명, 구현 코드, Level별 학습 자료가 포함된 노트:

- [내적](CONCEPT_내적.md) — 코사인 유사도의 분자, Self-Attention의 Q·K
- [L2 놈](CONCEPT_L2_놈.md) — 코사인 유사도의 분모, 벡터 정규화
- [코사인 유사도](CONCEPT_코사인_유사도.md) — 임베딩 검색의 핵심 거리 함수
- [Softmax와 Temperature](CONCEPT_Softmax_Temperature.md) — LLM 출력 확률 분포, temperature 파라미터
- [벡터 공간](CONCEPT_벡터_공간.md) — 임베딩이 사는 고차원 공간, 거리 함수 선택

## 추천 학습 경로

### 빠른 경로 (2시간) — Level 0 전체 + Level 1 핵심

수학에 자신 없는 학습자가 부트캠프 시작 전에 돌리는 코스.

```
1. 3Blue1Brown — Vectors (10분)
   https://www.youtube.com/watch?v=fNk_zzaMoSs
   → 벡터가 뭔지, 크기와 방향의 직관

2. 3Blue1Brown — Dot products (10분)
   https://www.youtube.com/watch?v=LyGKycYT2v0
   → 내적의 기하학적 의미, 투영으로 이해

3. StatQuest — Cosine Similarity (6분)
   https://www.youtube.com/watch?v=e9U0QAFbfLI
   → 코사인 유사도의 시각적 설명

4. StatQuest — Softmax (5분)
   https://www.youtube.com/watch?v=KpKog-L9veg
   → softmax가 확률 분포를 만드는 원리

5. Cohere — Temperature 가이드 (10분 읽기)
   https://cohere.com/blog/llm-parameters-best-practices-temperature
   → temperature가 텍스트 생성에 미치는 영향

6. Week 01 코사인 유사도 직접 구현 (30분)
   → 코드로 내적, L2 놈, 코사인 유사도 구현하면서 Level 1 달성
```

### 깊은 경로 (6시간) — Level 1 전체 + 실험

수학 기초가 있고, 원리를 더 깊이 이해하고 싶은 학습자.

```
1. 3Blue1Brown — Essence of Linear Algebra (Chapter 1-7) (2시간)
   https://www.youtube.com/playlist?list=PLZHQObOWTQDPD3MizzM2xVFitgF8hE_ab
   → 벡터, 기저, 행렬, 내적을 기하학적으로 완전히 이해

2. Vicki Boykis — What are embeddings? (1시간 읽기)
   https://vickiboykis.com/what_are_embeddings/
   → 임베딩의 역사, 수학적 배경, 실무 활용

3. Jay Alammar — The Illustrated Transformer (1시간 읽기)
   https://jalammar.github.io/illustrated-transformer/
   → Self-Attention의 Q·K 내적이 벡터 검색과 같은 수학이라는 연결

4. 직접 실험 3개 (2시간)
   → 코사인 유사도 float/double 정밀도 비교
   → Temperature에 따른 softmax 분포 시각화
   → FAQ 청크 임베딩 t-SNE 시각화
```

## References

- Jay Alammar, [The Illustrated Word2Vec](https://jalammar.github.io/illustrated-word2vec/) — 임베딩의 시각적 설명
- Jay Alammar, [The Illustrated Transformer](https://jalammar.github.io/illustrated-transformer/) — Self-Attention과 내적의 관계
- 3Blue1Brown, [Essence of Linear Algebra](https://www.youtube.com/playlist?list=PLZHQObOWTQDPD3MizzM2xVFitgF8hE_ab) — 벡터/내적/행렬의 직관
- Vicki Boykis, [What are embeddings?](https://vickiboykis.com/what_are_embeddings/) — 임베딩 deep dive
- Pinecone, [Vector Similarity Explained](https://www.pinecone.io/learn/vector-similarity/) — 코사인 vs 유클리드 vs 내적 비교
- Cohere, [Temperature Best Practices](https://cohere.com/blog/llm-parameters-best-practices-temperature) — temperature 실무 가이드

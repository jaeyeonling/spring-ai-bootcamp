# 코사인 유사도 (Cosine Similarity)

## 한 문장 정의

> 두 벡터 간의 각도(방향 유사성)를 -1 ~ 1 사이 값으로 표현한 것.  
> 1에 가까울수록 유사, 0은 무관, -1은 반대.

## 공식

```
cos(θ) = dot(a, b) / (|a| × |b|)

dot(a, b) = Σ a[i] × b[i]        — 내적
|v|        = √(Σ v[i]²)           — L2 놈 (벡터의 크기)
```

분자는 [내적](CONCEPT_내적.md), 분모는 두 벡터의 [L2 놈](CONCEPT_L2_놈.md) 곱이다. 내적을 크기로 나누는 것이므로 **벡터의 크기를 무시하고 방향만 비교**한다.

## 왜 코사인인가

[임베딩](CONCEPT_임베딩.md) 벡터는 크기(magnitude)가 달라도 방향이 같으면 의미가 같다.  
예: "환불"과 "환불 방법"은 벡터 크기가 다를 수 있지만 방향은 비슷하다.  
유클리드 거리는 크기에 영향을 받지만, 코사인 유사도는 방향만 본다.

| | 유클리드 거리 | 코사인 유사도 |
|-|:---:|:---:|
| `[1,2,3]` vs `[2,4,6]` | 거리 > 0 (다르다고 판단) | **1.0 (같다고 판단)** |
| 벡터 크기에 민감 | O | X |
| 임베딩 검색 기본 | X | **O** |

## 직접 구현 (Week 01)

```java
public static double cosineSimilarity(float[] a, float[] b) {
    double dot = 0.0, normA = 0.0, normB = 0.0;
    for (int i = 0; i < a.length; i++) {
        dot   += (double) a[i] * b[i];
        normA += (double) a[i] * a[i];
        normB += (double) b[i] * b[i];
    }
    if (normA == 0.0 || normB == 0.0) return 0.0; // 영벡터 안전 처리
    return dot / (Math.sqrt(normA) * Math.sqrt(normB));
}
```

> `float`를 `double`로 캐스팅하며 계산하는 이유: text-embedding-3-small은 1536차원이다. float(유효숫자 7자리)로 1536번 누적 연산하면 오차가 쌓여 유사도 0.891과 0.889의 순위가 뒤집힐 수 있다. double(유효숫자 15자리)로 계산하면 안전하다.

## 테스트 케이스로 검증

| 케이스 | 예시 | 기댓값 | 의미 |
|--------|------|--------|------|
| 동일 벡터 | `[1, 2, 3]` vs `[1, 2, 3]` | **1.0** | 완전히 같은 의미 |
| 직교 벡터 | `[1, 0]` vs `[0, 1]` | **0.0** | 전혀 무관한 의미 |
| 반대 벡터 | `[1, 2, 3]` vs `[-1, -2, -3]` | **-1.0** | 정반대 의미 |
| 크기만 다름 | `[1, 2, 3]` vs `[2, 4, 6]` | **1.0** | 의미는 같다 (방향 동일) |

## 고차원에서의 유사도 분포

실제 임베딩은 1536차원이다. 이 고차원 공간에서 랜덤 벡터 1000개의 코사인 유사도를 측정하면:
- 평균: ~0.0
- 표준편차: ~0.025
- 최대: ~0.1

**핵심 직관**: 고차원 랜덤 벡터끼리는 거의 다 직교(0에 가까움)이다. 따라서 임베딩 유사도 0.85는 "엄청나게 유사한" 것이다. 이것이 임베딩 검색이 작동하는 이유 — 의미적으로 관련된 텍스트만 높은 유사도를 보인다.

```python
import numpy as np
from sklearn.metrics.pairwise import cosine_similarity

# 1536차원 랜덤 벡터 1000개
vecs = np.random.randn(1000, 1536)
sims = cosine_similarity(vecs)
np.fill_diagonal(sims, 0)

print(f"평균: {sims.mean():.4f}")   # ~0.0
print(f"표준편차: {sims.std():.4f}") # ~0.025
print(f"최대: {sims.max():.4f}")     # ~0.1

# → 임베딩 유사도 0.85가 "얼마나 특별한 값인지" 체감
```

## Week 03+ 이후

Qdrant 등 벡터 DB는 코사인 유사도를 내부적으로 계산해준다.  
직접 구현할 필요 없이 `VectorStore.similaritySearch()` 호출만으로 충분.  
Week 01에서 직접 구현하는 이유: 내부 원리 체득.

벡터 DB는 추가로 벡터를 정규화(단위 벡터로 변환)해서 저장하는 최적화를 한다. 정규화된 벡터끼리는 **내적 = 코사인 유사도**이므로 L2 놈 계산을 생략할 수 있어 검색이 빨라진다.

## 학습 자료

### Level 0 (직감 잡기) — 10분

- **Normconf — WTF are embeddings?** (Josh Starmer)
  https://www.youtube.com/watch?v=wjZofJX0v4M
  > 임베딩 벡터가 무엇이고, 두 벡터의 "가까움"이 왜 유사도인지를 코사인 유사도 중심으로 설명. 수식 최소화, 그림 중심.

- **StatQuest — Cosine Similarity** (6분 영상)
  https://www.youtube.com/watch?v=e9U0QAFbfLI
  > 2D 좌표에서 각도가 줄어들수록 유사도가 올라가는 것을 시각적으로 보여줌. 유클리드 거리와의 차이도 다룸.

### Level 1 (공식 + 구현) — 30분

- **Wikipedia — Cosine similarity** (수식 섹션만)
  https://en.wikipedia.org/wiki/Cosine_similarity
  > 공식 정의, 각도와 유사도의 관계, 다른 거리 측정과의 비교.

- **직접 실험: 고차원에서 유사도 분포** (15분)
  > 위의 Python 코드 참조. 1536차원 랜덤 벡터의 유사도가 0 근처에 몰리는 것을 직접 확인.

### Level 1+ (디버깅 능력) — 20분

- **Pinecone — What is Cosine Similarity?**
  https://www.pinecone.io/learn/vector-similarity/
  > 벡터 DB 회사가 쓴 실용 가이드. 코사인 vs 유클리드 vs 내적의 사용 시나리오, 언제 어떤 거리 함수를 쓸지.

- **실험: float vs double 정밀도 차이** (10분)
  ```java
  // 1536차원 벡터에서 float 누적 vs double 누적의 오차
  // top-K 순위를 뒤집을 수 있는 수준인지 직접 확인
  float dotFloat = 0.0f;
  for (int i = 0; i < 1536; i++) dotFloat += a[i] * b[i];

  double dotDouble = 0.0;
  for (int i = 0; i < 1536; i++) dotDouble += (double) a[i] * b[i];
  ```

## 관련 개념

- [내적](CONCEPT_내적.md) — 코사인 유사도의 분자
- [L2 놈](CONCEPT_L2_놈.md) — 코사인 유사도의 분모
- [임베딩](CONCEPT_임베딩.md) — 비교할 벡터를 만드는 과정
- [벡터 공간](CONCEPT_벡터_공간.md) — 코사인 유사도가 측정하는 공간
- [RAG](CONCEPT_RAG.md) — 코사인 유사도로 관련 문서를 검색하는 패턴

## 등장한 주차

- Week 01 — `InMemoryVectorStore.cosineSimilarity()` 직접 구현

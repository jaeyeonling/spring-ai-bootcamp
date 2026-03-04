# L2 놈 (Magnitude)

## 한 문장 정의

> 벡터의 "크기" 또는 "길이". 원점에서 벡터가 가리키는 점까지의 직선 거리.

## 공식

```
|v| = √(Σ v[i]²)
    = √(v[0]² + v[1]² + ... + v[n-1]²)
```

2차원이면 피타고라스 정리 그 자체:
```
v = [3, 4]
|v| = √(9 + 16) = √25 = 5
```

## 직관적 이해

- 벡터 `[3, 4]`의 크기는 5. 원점에서 (3, 4) 점까지의 거리.
- 벡터 `[1, 0]`의 크기는 1. 이것이 **단위 벡터(unit vector)**.
- 벡터 `[0, 0, ..., 0]`의 크기는 0. **영벡터** — 코사인 유사도에서 0으로 나누기가 발생하므로 예외 처리 필요.

## 왜 "L2"인가

놈(norm)에는 여러 종류가 있다:

| 놈 | 공식 | 의미 | 용도 |
|----|------|------|------|
| L1 (맨해튼) | `Σ|v[i]|` | 성분의 절대값 합 | 희소 벡터, 정규화 |
| **L2 (유클리드)** | `√(Σ v[i]²)` | **직선 거리** | 코사인 유사도, 벡터 정규화 |
| L∞ (체비셰프) | `max(|v[i]|)` | 가장 큰 성분 | 게임 AI (체스판 거리) |

Applied AI에서는 **거의 항상 L2**. [코사인 유사도](CONCEPT_코사인_유사도.md)의 분모가 L2 놈이다.

## Applied AI에서 L2 놈이 등장하는 곳

### 1. 코사인 유사도의 분모

```
cos(θ) = dot(a, b) / (|a| × |b|)
                       ↑      ↑ 여기. 둘 다 L2 놈
```

### 2. 벡터 정규화 (normalization)

```
v_normalized = v / |v|    — 모든 성분을 L2 놈으로 나눔
```
결과: 크기가 1인 단위 벡터. 방향 정보만 남는다.

**왜 정규화하는가**: 정규화된 벡터끼리는 **[내적](CONCEPT_내적.md) = [코사인 유사도](CONCEPT_코사인_유사도.md)** 가 된다.
벡터 DB(Qdrant 등)는 임베딩을 정규화해서 저장 → 검색 시 내적만 계산하면 돼서 빠르다.

### 3. 유클리드 거리 vs 코사인 유사도

```
유클리드 거리: d = |a - b|  = √(Σ (a[i] - b[i])²)
코사인 유사도: cos(θ) = dot(a, b) / (|a| × |b|)
```

| | 유클리드 거리 | 코사인 유사도 |
|-|:---:|:---:|
| 벡터 크기에 영향 | O | X |
| `[1,2,3]` vs `[2,4,6]` | 거리 > 0 (다름) | 유사도 = 1.0 (같음) |
| 의미 | 두 점이 얼마나 먼가 | 두 방향이 얼마나 비슷한가 |
| 임베딩 검색에서 | 잘 안 씀 | **기본 선택** |

[임베딩](CONCEPT_임베딩.md)은 "의미의 방향"이 중요하지 "크기"가 중요하지 않으므로 코사인이 적합하다.

## Java 구현

```java
// 코사인 유사도 내부에서 L2 놈을 계산하는 부분
double normA = 0.0;
for (int i = 0; i < a.length; i++) {
    normA += (double) a[i] * a[i];  // 각 성분의 제곱 합
}
double magnitude = Math.sqrt(normA);  // 제곱근 = L2 놈

// 영벡터 안전 처리 — Week 01 구현에서 이미 적용
if (normA == 0.0 || normB == 0.0) return 0.0;
```

## 학습 자료

### Level 0 (직감 잡기) — 10분

- **3Blue1Brown — Vectors, what even are they?** (첫 2분만 봐도 충분)
  https://www.youtube.com/watch?v=fNk_zzaMoSs
  > 벡터를 "화살표"로 시각화. 크기 = 화살표의 길이라는 직관.

- **betterexplained — Understanding Pythagorean Distance**
  https://betterexplained.com/articles/measure-any-distance-with-the-pythagorean-theorem/
  > 피타고라스 정리가 2D → 3D → nD로 일반화되는 과정. L2 놈이 피타고라스 정리의 확장이라는 것.

### Level 1 (공식 이해) — 20분

- **Khan Academy — Vector magnitude**
  https://www.khanacademy.org/math/linear-algebra/vectors-and-spaces/vectors/v/vector-introduction-linear-algebra
  > 2D에서 시작해 n차원으로 확장. 연습 문제 포함.

- **직접 실험: 정규화의 효과 확인** (10분)
  ```python
  import numpy as np
  a = np.array([1, 2, 3], dtype=float)
  b = np.array([2, 4, 6], dtype=float)  # a의 2배

  # 정규화 전: 유클리드 거리가 0이 아님
  print(np.linalg.norm(a - b))  # 3.74...

  # 코사인 유사도: 방향이 같으므로 1.0
  print(np.dot(a, b) / (np.linalg.norm(a) * np.linalg.norm(b)))  # 1.0

  # 정규화 후: 같은 벡터가 됨
  a_norm = a / np.linalg.norm(a)
  b_norm = b / np.linalg.norm(b)
  print(np.allclose(a_norm, b_norm))  # True
  ```

## 관련 개념

- [내적](CONCEPT_내적.md) — L2 놈과 함께 코사인 유사도를 구성
- [코사인 유사도](CONCEPT_코사인_유사도.md) — L2 놈으로 정규화한 내적
- [벡터 공간](CONCEPT_벡터_공간.md) — L2 놈은 벡터 공간에서의 거리 측정 도구
- [임베딩](CONCEPT_임베딩.md) — 벡터 정규화가 검색 성능에 영향

## 등장한 주차

- Week 01 — `cosineSimilarity()` 내부에서 `Math.sqrt(normA)` 계산

# 내적 (Dot Product)

## 한 문장 정의

> 두 벡터의 대응하는 성분끼리 곱한 뒤 모두 더한 값. 두 벡터가 "얼마나 같은 방향인지"를 하나의 숫자로 표현한다.

## 공식

```
dot(a, b) = Σ a[i] × b[i]
          = a[0]×b[0] + a[1]×b[1] + ... + a[n-1]×b[n-1]
```

## 직관적 이해

내적의 결과값이 의미하는 것:

| 결과 | 의미 | 예시 |
|------|------|------|
| 양수 (큰 값) | 같은 방향 | "환불"과 "반품"의 임베딩 |
| 0 | 직교 (무관) | "환불"과 "날씨"의 임베딩 |
| 음수 | 반대 방향 | 실제 임베딩에서는 드묾 |

내적은 **"투영(projection)"** 으로도 이해할 수 있다:
- a를 b 위에 그림자를 떨어뜨린다고 상상
- 그림자의 길이 x b의 길이 = 내적
- 같은 방향이면 그림자가 길고(양수), 수직이면 그림자가 없고(0), 반대면 뒤로 떨어짐(음수)

## Applied AI에서 내적이 등장하는 곳

### 1. 코사인 유사도의 분자

```
cos(θ) = dot(a, b) / (|a| × |b|)
            ↑ 여기
```

코사인 유사도는 내적을 정규화한 것. 내적만 쓰면 벡터 크기에 따라 값이 달라지므로 크기로 나눠준다.

### 2. Self-Attention의 Q·K

Transformer(GPT 등의 LLM 기반 아키텍처)에서 "이 토큰이 저 토큰에 얼마나 주목해야 하는가"를 계산할 때 사용하는 핵심 연산이 내적이다:

```
score = Q · Kᵀ    — 이것이 내적
attention = softmax(score / √d_k)
```

Query(Q) 벡터와 Key(K) 벡터의 내적이 클수록 두 토큰 간의 관련도가 높다고 판단한다. 이것은 임베딩 검색에서 질문 벡터와 문서 벡터의 내적(코사인 유사도)으로 관련도를 측정하는 것과 **동일한 수학**이다.

즉, LLM 내부의 attention 메커니즘과 RAG의 벡터 검색은 같은 원리로 동작한다.

### 3. 정규화된 벡터에서 내적 = 코사인 유사도

벡터가 단위 벡터(크기 1)로 정규화되어 있으면:
```
|a| = 1, |b| = 1
cos(θ) = dot(a, b) / (1 × 1) = dot(a, b)
```
→ **내적만 계산하면 코사인 유사도가 나온다**. Qdrant 같은 벡터 DB가 벡터를 정규화해서 저장하는 이유: [L2 놈](CONCEPT_L2_놈.md) 계산을 생략할 수 있어 검색이 빨라진다.

## Java 구현

```java
// 기본 구현
double dot = 0.0;
for (int i = 0; i < a.length; i++) {
    dot += (double) a[i] * b[i];  // float → double 캐스팅으로 정밀도 확보
}

// Java 16+ Vector API (SIMD 가속) — 프로덕션에서 성능이 중요할 때
// jdk.incubator.vector 패키지 사용 가능하지만 이 부트캠프에서는 불필요
```

## 학습 자료

### Level 0 (직감 잡기) — 15분

- **3Blue1Brown — Dot products and duality** (10분 영상)
  https://www.youtube.com/watch?v=LyGKycYT2v0
  > 내적을 "한 벡터를 다른 벡터 위에 투영한 길이"로 시각화. 수식 없이 기하학적 직관부터 잡아준다.

- **betterexplained — Vector Calculus: Understanding the Dot Product**
  https://betterexplained.com/articles/vector-calculus-understanding-the-dot-product/
  > "두 벡터가 얼마나 같은 방향으로 일하는가(work)"라는 물리적 직관. 그림 중심 설명.

### Level 1 (공식 이해) — 30분

- **Khan Academy — Dot Product** (선형대수 코스)
  https://www.khanacademy.org/math/linear-algebra/vectors-and-spaces/dot-cross-products/v/vector-dot-product-and-vector-length
  > 공식 유도 + 연습 문제. `dot(a,b) = |a||b|cos(θ)`의 관계를 단계별로 증명.

- **직접 실험: Python/Java로 검증** (10분)
  ```python
  import numpy as np
  a = np.array([1.0, 2.0, 3.0])
  b = np.array([4.0, 5.0, 6.0])
  print(np.dot(a, b))  # 32.0 = 1*4 + 2*5 + 3*6
  ```

### Level 1+ (Applied AI 맥락) — 20분

- **Jay Alammar — The Illustrated Transformer**
  https://jalammar.github.io/illustrated-transformer/
  > Self-Attention에서 Q·K 내적이 어떻게 사용되는지 시각화. 내적이 "주목도 점수"로 변환되는 과정.

## 관련 개념

- [코사인 유사도](CONCEPT_코사인_유사도.md) — 내적을 정규화한 것
- [L2 놈](CONCEPT_L2_놈.md) — 내적의 분모에 사용
- [Softmax와 Temperature](CONCEPT_Softmax_Temperature.md) — Self-Attention에서 내적 결과를 확률로 변환
- [임베딩](CONCEPT_임베딩.md) — 내적으로 비교할 벡터를 생성

## 등장한 주차

- Week 01 — `InMemoryVectorStore.cosineSimilarity()` 내부에서 내적 계산

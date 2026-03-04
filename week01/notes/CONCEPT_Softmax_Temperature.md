# Softmax와 Temperature

## 한 문장 정의

> **Softmax**: 임의의 실수 벡터를 확률 분포(합=1, 모두 양수)로 변환하는 함수.  
> **Temperature**: softmax의 "뾰족함"을 조절하는 스케일링 파라미터.

## 공식

```
softmax(x_i) = exp(x_i) / Σ exp(x_j)

temperature 적용:
softmax(x_i, T) = exp(x_i / T) / Σ exp(x_j / T)
```

## 직관적 이해

### Softmax가 하는 일

LLM의 마지막 레이어는 어휘(vocabulary)의 각 토큰에 대한 점수(logit)를 출력한다. 이 점수는 그냥 실수이므로 "확률"이 아니다. Softmax가 이것을 확률로 변환한다:

```
"환불 절차는" 다음 토큰 점수(logit):
  "다음" → 5.2
  "아래" → 4.8
  "먼저" → 2.1
  "바나나" → -3.0

softmax 적용 후 (확률):
  "다음" → 0.58
  "아래" → 0.39
  "먼저" → 0.03
  "바나나" → 0.0002
```

왜 exp를 쓰는가:
- exp는 항상 양수 → 음수 점수도 양수 확률로 변환
- exp는 차이를 증폭 → 높은 점수가 더 높은 확률로 강조
- 합으로 나누면 전체 합이 1 → 확률 분포 완성

### Temperature가 바꾸는 것

Temperature(T)는 점수를 T로 나눠서 분포의 모양을 바꾼다:

```
T = 0.1 (낮음):  점수 차이가 극대화 → 최고 확률 토큰이 거의 100%
T = 1.0 (기본):  원래 분포 그대로
T = 2.0 (높음):  점수 차이가 축소 → 확률이 고르게 분산
```

같은 logit `[5.2, 4.8, 2.1, -3.0]`에 대해:

| T | "다음" | "아래" | "먼저" | "바나나" | 체감 |
|---|:------:|:------:|:------:|:--------:|------|
| 0.1 | 98.2% | 1.8% | ~0% | ~0% | 항상 같은 답 |
| 0.5 | 69% | 28% | 3% | ~0% | 가끔 다른 답 |
| 1.0 | 58% | 39% | 3% | 0.02% | 자연스러운 다양성 |
| 2.0 | 42% | 37% | 14% | 7% | 엉뚱한 답 가능 |

## Week 01에서 temperature: 0.1인 이유

```yaml
# application.yml
spring.ai.openai.chat.options:
  temperature: 0.1
```

FAQ 챗봇의 목적은 **정확한 사실 전달**이다.
- "반품 기간은 14일입니다"를 100번 물어보면 100번 같은 답이어야 한다
- 창의적인 답변이 필요한 곳이 아니다
- T=0.1은 "가장 확률 높은 토큰을 거의 항상 선택"하게 만든다

**반대로 T를 높여야 하는 경우**: 창작 글쓰기, 브레인스토밍, 대화형 챗봇에서 반복 방지.

## Applied AI에서 Softmax/Temperature가 등장하는 곳

### 1. LLM의 텍스트 생성 (위 내용)

### 2. Self-Attention의 attention score

Transformer 내부에서도 softmax가 사용된다:

```
attention = softmax(Q·Kᵀ / √d_k)
```

여기서 `√d_k`는 temperature와 비슷한 역할 — [내적](CONCEPT_내적.md) 값이 차원 수에 비례해 커지는 것을 방지하는 스케일링이다. 이것이 없으면 softmax의 gradient가 소멸해 학습이 어려워진다.

### 3. top-p (nucleus sampling)와의 관계

```
temperature → 분포의 모양을 바꿈
top-p → 분포에서 상위 p%만 남기고 나머지를 잘라냄
```

둘 다 "다음 토큰 선택의 다양성"을 조절하지만 메커니즘이 다르다:
- Temperature: 확률 분포 자체를 변형
- top-p: 확률 분포는 유지하되 낮은 확률 토큰을 제거

Spring AI에서는 둘 다 설정 가능:
```yaml
spring.ai.openai.chat.options:
  temperature: 0.1
  top-p: 0.9
```

### 4. ReRanker에서의 score 정규화

ReRanker가 여러 청크의 관련도 점수를 반환할 때, softmax로 점수를 확률로 변환해 비교 가능하게 만드는 패턴이 Week 04에서 등장한다.

## 학습 자료

### Level 0 (직감 잡기) — 15분

- **StatQuest — Softmax** (5분 영상)
  https://www.youtube.com/watch?v=KpKog-L9veg
  > exp를 왜 쓰는지, 결과가 왜 확률이 되는지를 그래프로 시각화.

- **Cohere — LLM Parameters Demystified: Temperature**
  https://cohere.com/blog/llm-parameters-best-practices-temperature
  > temperature 값에 따라 텍스트 생성이 어떻게 달라지는지 실제 예시와 함께 설명. Applied AI 실무 관점.

### Level 1 (공식 + 실험) — 30분

- **직접 실험: Temperature에 따른 분포 변화** (15분)
  ```python
  import numpy as np
  import matplotlib.pyplot as plt

  logits = np.array([5.2, 4.8, 2.1, -3.0])
  labels = ['"다음"', '"아래"', '"먼저"', '"바나나"']

  def softmax(x, T=1.0):
      e = np.exp(x / T)
      return e / e.sum()

  fig, axes = plt.subplots(1, 4, figsize=(16, 4))
  for ax, T in zip(axes, [0.1, 0.5, 1.0, 2.0]):
      probs = softmax(logits, T)
      ax.bar(labels, probs)
      ax.set_title(f'T = {T}')
      ax.set_ylim(0, 1)

  plt.tight_layout()
  plt.savefig('temperature_comparison.png')
  ```

- **OpenAI Cookbook — How to use logprobs**
  https://cookbook.openai.com/examples/using_logprobs
  > 실제 API에서 logprobs를 활성화해 토큰 확률을 확인하는 방법. Temperature가 실제로 확률을 어떻게 바꾸는지 실험.

### Level 1+ (뉘앙스 이해) — 20분

- **Anthropic — Temperature and Sampling**
  https://docs.anthropic.com/en/docs/build-with-claude/prompt-engineering/use-temperature-and-sampling
  > temperature, top-p, top-k의 조합 가이드. 작업 유형별 권장 설정.

## 관련 개념

- [내적](CONCEPT_내적.md) — Self-Attention에서 softmax 입력이 되는 Q·K 내적
- [벡터 공간](CONCEPT_벡터_공간.md) — softmax가 내적 결과를 확률 분포로 변환
- [임베딩](CONCEPT_임베딩.md) — 임베딩 모델의 학습 과정에서 softmax 사용

## 등장한 주차

- Week 01 — `application.yml`에서 `temperature: 0.1` 설정
- Week 02 — 프롬프트 엔지니어링에서 temperature 튜닝

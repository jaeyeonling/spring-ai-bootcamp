# 벡터 공간 (Vector Space)

## 한 문장 정의

> 벡터(방향과 크기를 가진 수의 배열)들이 사는 공간. [임베딩](CONCEPT_임베딩.md)은 텍스트를 이 공간의 한 점으로 매핑한다.

## 왜 Applied AI에서 알아야 하는가

임베딩 검색은 결국 "벡터 공간에서 가까운 점 찾기"다. 이 공간이 어떤 성질을 가지는지 알면:

- 검색 결과가 왜 이렇게 나오는지 디버깅 가능
- 벡터 DB의 인덱싱 전략(HNSW 등)이 왜 필요한지 이해
- 차원 축소 시각화(t-SNE, UMAP)가 왜 유용한지 이해

## 핵심 개념 3가지

### 1. 차원 (Dimension)

벡터의 성분 수 = 공간의 차원.

```
2차원: [x, y]           — 평면의 점
3차원: [x, y, z]        — 3D 공간의 점
1536차원: [x₀, x₁, ..., x₁₅₃₅]  — text-embedding-3-small의 출력
```

> **고차원의 직관**: 1536차원을 시각화할 수 없지만, 2~3차원에서의 직관(거리, 방향, 각도)이 그대로 유지된다. [내적](CONCEPT_내적.md), [코사인 유사도](CONCEPT_코사인_유사도.md) 공식이 차원에 관계없이 동일하게 작동한다.

### 2. 거리 (Distance)

같은 두 점 사이의 "가까움"을 측정하는 방법이 여러 가지:

| 거리 함수 | 의미 | 수식 | 임베딩에서 |
|-----------|------|------|-----------|
| 유클리드 (L2) | 직선 거리 | `√(Σ(a[i]-b[i])²)` | 크기 차이에 민감 |
| **코사인** | 방향의 유사성 | `1 - cos(θ)` | **기본 선택** |
| 내적 (IP) | 크기x방향 | `-dot(a,b)` | 정규화된 벡터에서 |
| 맨해튼 (L1) | 격자 거리 | `Σ|a[i]-b[i]|` | 희소 벡터에서 |

벡터 DB 설정 시 거리 함수를 선택해야 한다:
```python
# Qdrant 컬렉션 생성 시 (Week 03)
client.create_collection(
    collection_name="faq",
    vectors_config=VectorParams(
        size=1536,
        distance=Distance.COSINE  # ← 여기서 선택
    )
)
```

### 3. 차원의 저주 (Curse of Dimensionality)

고차원 공간에서 나타나는 반직관적 현상:

- **모든 점이 비슷하게 멀다**: 1536차원에서 랜덤 벡터끼리의 코사인 유사도는 거의 0 (직교에 가까움)
- **"가까운 이웃"이 의미 있다**: 임베딩으로 만든 벡터는 랜덤이 아니라 의미 구조를 반영하므로, 가까운 것은 진짜 가까운 것
- **브루트포스 검색이 느리다**: 모든 벡터와 비교하면 O(n). 이것이 Week 03에서 벡터 DB(ANN 인덱스)가 필요한 이유

## 시각화: 고차원을 2D로 내려보기

실제로 임베딩 벡터를 시각화하려면 차원 축소가 필요하다:

```python
from sklearn.manifold import TSNE
import matplotlib.pyplot as plt

# embeddings: (N, 1536) 배열 — 각 청크의 임베딩
embeddings_2d = TSNE(n_components=2, perplexity=5).fit_transform(embeddings)

plt.scatter(embeddings_2d[:, 0], embeddings_2d[:, 1])
for i, label in enumerate(chunk_labels):
    plt.annotate(label, embeddings_2d[i])
plt.show()

# → "Shipping" 관련 청크들이 한 곳에 모이고,
#    "Payment" 관련 청크들이 다른 곳에 모이는 것을 볼 수 있다
```

> **주의**: t-SNE/UMAP은 시각화 도구일 뿐, 검색에 쓰면 안 된다. 차원 축소 과정에서 거리 관계가 왜곡된다.

## 학습 자료

### Level 0 (직감 잡기) — 20분

- **3Blue1Brown — Essence of Linear Algebra** (Chapter 1-3)
  https://www.youtube.com/playlist?list=PLZHQObOWTQDPD3MizzM2xVFitgF8hE_ab
  > 벡터가 뭔지, 벡터의 덧셈/스칼라곱이 기하학적으로 뭘 의미하는지. 처음 3개 영상(30분)이면 충분. 선형대수 직관을 잡는 최고의 자료.

- **Vicki Boykis — What are embeddings?**
  https://vickiboykis.com/what_are_embeddings/
  > ML 엔지니어가 쓴 임베딩 deep dive. "벡터 공간에 텍스트를 매핑한다"는 것이 실제로 무슨 뜻인지, 역사와 함께 설명.

### Level 1 (고차원 직관) — 30분

- **Embedding Projector (TensorFlow)**
  https://projector.tensorflow.org/
  > 브라우저에서 직접 임베딩 벡터를 3D로 시각화. Word2Vec 임베딩으로 "king - man + woman = queen"을 직접 확인할 수 있다.

- **직접 실험: FAQ 청크 임베딩 시각화** (20분)
  ```python
  import numpy as np
  from openai import OpenAI
  from sklearn.manifold import TSNE
  import matplotlib.pyplot as plt

  client = OpenAI()
  
  chunks = [
      "How soon after ordering will my package be dispatched?",
      "What shipping options are available?",
      "Under what conditions can I return a product?",
      "How do I initiate a return?",
      "Which credit cards do you accept?",
      "Do you offer installment plans?",
  ]
  categories = ["Shipping", "Shipping", "Returns", "Returns", "Payment", "Payment"]
  
  embeddings = [client.embeddings.create(
      input=c, model="text-embedding-3-small"
  ).data[0].embedding for c in chunks]
  embeddings = np.array(embeddings)
  
  tsne = TSNE(n_components=2, perplexity=2, random_state=42)
  coords = tsne.fit_transform(embeddings)
  
  colors = {"Shipping": "blue", "Returns": "red", "Payment": "green"}
  for i, (x, y) in enumerate(coords):
      plt.scatter(x, y, c=colors[categories[i]], s=100)
      plt.annotate(chunks[i][:20], (x, y), fontsize=8)
  plt.title("FAQ Chunk Embeddings (t-SNE)")
  plt.show()
  
  # → 같은 카테고리의 청크가 가까이 모이는 것을 확인
  ```

### Level 1+ (벡터 DB 이해를 위한 배경) — 선택

- **Pinecone — What is a Vector Database?**
  https://www.pinecone.io/learn/vector-database/
  > 브루트포스 검색 → ANN(Approximate Nearest Neighbor) → HNSW 인덱스로 이어지는 필요성. Week 03 Qdrant 사용 전에 읽으면 유용.

- **James Briggs — HNSW Explained**
  https://www.youtube.com/watch?v=QvKMwLjdK-s
  > 고차원 벡터 검색에서 가장 널리 쓰이는 인덱스 구조(HNSW)의 직관적 설명. 그래프 기반 탐색이 왜 빠른지.

## 관련 개념

- [임베딩](CONCEPT_임베딩.md) — 텍스트를 벡터 공간의 점으로 변환
- [코사인 유사도](CONCEPT_코사인_유사도.md) — 벡터 공간에서 가까움을 측정
- [내적](CONCEPT_내적.md) — 벡터 공간의 기본 연산
- [L2 놈](CONCEPT_L2_놈.md) — 벡터 공간에서의 거리 측정

## 등장한 주차

- Week 01 — 인메모리 벡터 공간에서 코사인 유사도 검색
- Week 03 — Qdrant 벡터 DB: 벡터 공간의 영구 저장소 + ANN/HNSW
- Week 04 — 하이브리드 검색: dense vector + sparse vector 공간의 결합

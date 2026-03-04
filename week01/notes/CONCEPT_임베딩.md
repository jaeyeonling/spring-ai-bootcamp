# 임베딩 (Embedding)

## 한 문장 정의

> 텍스트(또는 이미지, 오디오 등)를 고차원 실수 벡터로 변환하는 것.  
> 의미가 비슷한 텍스트는 [벡터 공간](CONCEPT_벡터_공간.md)에서 가까운 위치에 놓인다.

## 왜 필요한가

컴퓨터는 텍스트를 직접 비교할 수 없다. "환불"과 "refund"는 완전히 다른 문자열이지만 의미는 같다. 임베딩은 이 의미적 유사성을 수치로 표현한다.

키워드 검색과의 차이:

| | 키워드 검색 | 임베딩 검색 |
|-|------------|------------|
| 비교 방식 | 문자열 일치 | [코사인 유사도](CONCEPT_코사인_유사도.md)로 벡터 비교 |
| "환불" vs "refund" | 다름 | 가까움 |
| "환불" vs "배송" | 다를 수 있음 | 멀다 |
| 동의어 처리 | 불가 | 가능 |
| 다국어 | 불가 | 가능 (모델에 따라) |

## 어떻게 동작하는가

```
"환불은 어떻게 받나요?" 
    → EmbeddingModel
    → [0.023, -0.451, 0.187, ..., 0.034]  (1536차원, text-embedding-3-small 기준)
```

- 각 차원은 의미의 특정 측면을 인코딩 (해석 불가능한 잠재 공간)
- 같은 모델을 사용해야 비교 가능 — 모델이 다르면 벡터 공간이 다름
- 차원 수는 모델에 따라 다름 (text-embedding-3-small: 1536, ada-002: 1536, text-embedding-3-large: 3072)

## 내부 원리 (알면 좋지만 필수는 아님)

임베딩 모델은 Transformer 아키텍처 기반의 신경망이다:

1. 입력 텍스트를 토큰으로 분할
2. 각 토큰이 Transformer 레이어를 통과하며 문맥 정보를 흡수
3. 마지막 레이어의 출력을 평균(pooling)해서 하나의 벡터로 압축

이 과정에서 핵심이 **Self-Attention**: 각 토큰이 다른 모든 토큰과의 관련도를 [내적](CONCEPT_내적.md)으로 계산하고, [Softmax와 Temperature](CONCEPT_Softmax_Temperature.md)로 확률 분포로 변환한 뒤, 가중합으로 새로운 표현을 만든다. 이것이 "문맥을 이해한 벡터"가 되는 원리다.

**다국어가 되는 이유**: OpenAI의 임베딩 모델은 100+ 언어를 같은 벡터 공간에서 학습(multilingual training). "환불"과 "refund"가 같은 문맥에서 사용되는 대량의 텍스트로 학습했기 때문에, 다른 언어더라도 의미가 같으면 가까운 벡터가 나온다.

## Spring AI에서 사용법

```java
// EmbeddingModel은 Spring AI가 제공하는 추상 인터페이스
// application.yml에 설정한 모델(text-embedding-3-small 등)로 자동 주입
@Autowired
EmbeddingModel embeddingModel;

float[] vector = embeddingModel.embed("환불은 어떻게 받나요?");
// float[] — 벡터 차원 수는 모델에 따라 다름 (text-embedding-3-small: 1536)
```

## 비용과 속도

| | 임베딩 API | Chat API (GPT-4o-mini) |
|-|:---:|:---:|
| 1K 토큰 비용 | ~$0.00002 | ~$0.00015 (입력) |
| 속도 | 매우 빠름 | 상대적으로 느림 |
| 호출 시점 | 문서: 1회, 질문: 매 요청 | 매 요청 |

- 문서 임베딩은 앱 시작 시 1회 or 변경 시에만 수행 → 런타임 비용 낮음
- 질문 임베딩은 매 요청마다 발생하지만 빠르고 저렴

## 학습 자료

### Level 0 (직감 잡기) — 15분

- **Normconf — WTF are embeddings?** (Josh Starmer)
  https://www.youtube.com/watch?v=wjZofJX0v4M
  > 임베딩이 뭔지, 왜 텍스트를 숫자로 바꾸는지를 비개발자도 이해할 수 있게 설명.

- **OpenAI — Embeddings Guide**
  https://platform.openai.com/docs/guides/embeddings
  > 공식 가이드. 어떤 모델을 쓸지, 어떤 용도에 적합한지 빠르게 파악.

### Level 1 (원리 이해) — 45분

- **Jay Alammar — The Illustrated Word2Vec**
  https://jalammar.github.io/illustrated-word2vec/
  > 임베딩의 시초인 Word2Vec의 학습 과정을 시각적으로 설명. "주변 단어를 예측하도록 학습하면 의미가 비슷한 단어가 가까워진다"는 핵심 원리.

- **Vicki Boykis — What are embeddings?**
  https://vickiboykis.com/what_are_embeddings/
  > Word2Vec → BERT → Sentence Transformers로 이어지는 임베딩의 역사. 실무에서 임베딩 모델을 선택하는 기준.

- **Embedding Projector (TensorFlow)**
  https://projector.tensorflow.org/
  > 브라우저에서 임베딩 벡터를 3D로 시각화. "king - man + woman = queen" 같은 벡터 산술을 직접 확인.

### Level 1+ (모델 선택 가이드) — 선택

- **MTEB Leaderboard**
  https://huggingface.co/spaces/mteb/leaderboard
  > 임베딩 모델 벤치마크. 다국어 성능, 검색 성능, 차원 수를 비교해서 프로젝트에 맞는 모델 선택.

## 관련 개념

- [코사인 유사도](CONCEPT_코사인_유사도.md) — 임베딩 벡터 간 유사도 측정 방법
- [벡터 공간](CONCEPT_벡터_공간.md) — 임베딩이 사는 고차원 공간
- [RAG](CONCEPT_RAG.md) — 임베딩을 검색에 활용하는 패턴
- [청킹](CONCEPT_청킹.md) — 임베딩하기 전 문서를 분할하는 전략
- [내적](CONCEPT_내적.md) — 임베딩 비교의 핵심 수학 연산

## 등장한 주차

- Week 01 — `EmbeddingModel.embed()` 직접 사용, InMemoryVectorStore 구현

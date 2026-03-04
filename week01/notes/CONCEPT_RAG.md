# RAG (Retrieval-Augmented Generation)

## 한 문장 정의

> LLM에게 전체 지식베이스를 넣는 대신, 질문과 관련된 문서만 검색해서 컨텍스트로 제공하는 패턴.

## 왜 필요한가

LLM은 토큰 한계가 있고, 토큰은 비용이다.  
전체 FAQ를 매번 넣으면:
- 비용이 선형으로 증가 (문서가 커질수록)
- 관련 없는 정보가 노이즈로 작용해 답변 품질 저하
- "Lost in the Middle" 현상: LLM은 긴 컨텍스트의 앞부분과 끝부분에 집중하고 중간을 놓치는 경향이 있다 ([Liu et al., 2023](https://arxiv.org/abs/2307.03172))
- 컨텍스트 한계(128K 등) 도달 시 자르거나 실패

RAG는 "필요한 것만 골라서 넣는다".

## 3단계 흐름

```
[R] Retrieval — 검색
    질문 → 임베딩 → 벡터 공간에서 가장 가까운 문서 청크 top-K 반환

[A] Augmented — 증강
    검색된 청크를 시스템 프롬프트의 컨텍스트로 삽입

[G] Generation — 생성
    LLM이 주어진 컨텍스트 기반으로 답변 생성
```

## 코드로 표현하면

```java
// R: 검색
float[] queryVector = embeddingModel.embed(question);
List<String> chunks = vectorStore.search(queryVector, topK=3);
String context = String.join("\n\n---\n\n", chunks);

// A: 증강 (시스템 프롬프트에 컨텍스트 삽입)
String systemPrompt = "FAQ 내용만으로 답하세요:\n" + context;

// G: 생성
ChatResponse response = chatClient.prompt()
    .system(systemPrompt)
    .user(question)
    .call()
    .chatResponse();
```

## Naive RAG vs Advanced RAG

| | Naive RAG (Week 01-02) | Advanced RAG (Week 04+) |
|-|------------------------|-------------------------|
| 검색 | [코사인 유사도](CONCEPT_코사인_유사도.md) top-K | 2-Stage Retrieval (ReRanking) |
| 쿼리 | 원본 질문 그대로 | 쿼리 변환 (확장/재작성) |
| 결과 병합 | 해당 없음 | Reciprocal Rank Fusion (RRF) |
| [청킹](CONCEPT_청킹.md) | 고정 구분자 | TokenTextSplitter, 시맨틱 청킹 |
| 평가 | 수동 | `RelevancyEvaluator` 자동화 |

## 핵심 설계 결정들

### 시스템 프롬프트에서 Grounding

"FAQ 내용만으로 답하세요"라는 지시가 핵심이다:
- **환각(Hallucination) 방지**: 이 지시가 없으면 LLM은 자기 학습 데이터에서 "그럴듯한 답"을 만들어낸다. 반품 기간이 14일인데 LLM이 일반적인 30일을 답할 수 있다.
- **"모르면 모른다"를 명시**: 이 문구가 빠지면 FAQ에 없는 질문에도 자신있게 답을 만들어낸다.

### top-K 선택

| K | 프롬프트 토큰 | 정확도(recall) | 노이즈 |
|---|:----------:|:-----------:|:-----:|
| 1 | 최소 | 정답이 2위면 놓침 | 없음 |
| 3 | 적당 | 대부분 커버 | 낮음 |
| 5+ | 큼 | 거의 완벽 | 무관 청크가 답변 품질 저하 |

### Temperature 설정

FAQ 챗봇에서 `temperature: 0.1`을 쓰는 이유: 같은 질문에 항상 같은 정확한 답이 나와야 한다.
자세한 내용은 [Softmax와 Temperature](CONCEPT_Softmax_Temperature.md) 참조.

## 한계와 Week별 개선

| 한계 | 체감 시점 | 해결 주차 |
|------|----------|-----------|
| top-K 중 정답이 1위가 아님 | 관련은 있지만 최선이 아닌 청크가 1위 | Week 04 (ReRanker) |
| 청크가 너무 크거나 작음 | 긴 Q&A는 토큰 낭비, 짧은 Q&A는 맥락 부족 | Week 02 (TokenTextSplitter) |
| 재시작 시 [임베딩](CONCEPT_임베딩.md) 데이터 손실 | 매번 시작 시 API 호출 = 비용 + 시간 | Week 03 (Qdrant) |
| 답변 품질 측정 불가 | "잘 되는 것 같은데?"라는 감에 의존 | Week 02 (RelevancyEvaluator) |
| 환각이 일어나도 알 방법이 없음 | 14일인데 30일이라고 답하면? | Week 05 (환각 탐지) |

## 실제 프로덕션에서의 RAG 구축

Week 01처럼 모든 단계를 직접 구현하는 것은 교육 목적이다. 실제로는 프레임워크나 매니지드 서비스가 대부분의 단계를 추상화한다.

### Week 01 (교육용) vs 프로덕션

| | Week 01 | 프로덕션 |
|-|---------|---------|
| 벡터 저장 | `ArrayList` | Qdrant, Pinecone, pgvector 등 |
| 유사도 검색 | `for` 루프 O(n) 전수 검색 | ANN(Approximate Nearest Neighbor) 인덱스 O(log n) |
| 청킹 | 수동 정규식 `"### "` | TokenTextSplitter, semantic chunking |
| 프롬프트 조립 | 문자열 연결 | Advisor/Chain 패턴으로 추상화 |
| 검색 품질 | top-K만 | ReRanker, 하이브리드 검색, 쿼리 변환 |
| 임베딩 영속성 | 앱 재시작 시 소실 | 벡터 DB에 영구 저장 |

### 접근법 1: 프레임워크 기반 (코드 작성)

Spring AI, LangChain, LlamaIndex 등이 RAG 파이프라인의 각 단계를 추상화한다.

**Spring AI** (Week 03+에서 전환):

```java
// 인제스트: 문서 → 청킹 → 임베딩 → 벡터 DB 저장을 한 줄로
vectorStore.add(new TokenTextSplitter().apply(
    new TikaDocumentReader(resource).get()));

// 쿼리: QuestionAnswerAdvisor가 검색 → 프롬프트 구성 → LLM 호출을 자동화
ChatClient.builder(chatModel)
    .defaultAdvisors(new QuestionAnswerAdvisor(vectorStore))
    .build()
    .prompt().user(question).call().content();
```

Week 01에서 직접 만든 `InMemoryVectorStore`, `cosineSimilarity()`, 프롬프트 수동 조립이 전부 프레임워크 안에 감춰진다.

### 접근법 2: 매니지드 서비스

| 서비스 | 방식 | 특징 |
|--------|------|------|
| **OpenAI Assistants API** | 파일 업로드 → 자동 청킹 + 임베딩 + 검색 | 가장 간단. 벡터 DB 관리 불필요 |
| **AWS Bedrock Knowledge Bases** | S3에 문서 → 자동 RAG 파이프라인 | 인프라 관리 불필요 |
| **Azure AI Search + OpenAI** | Azure Portal에서 인덱스 생성 → OpenAI 연결 | 엔터프라이즈에서 많이 사용 |
| **Google Vertex AI RAG Engine** | Cloud 문서 업로드 → 자동 구축 | GCP 생태계 |

### 직접 구현의 가치

프레임워크가 다 해준다면 왜 Week 01에서 직접 만들었는가?

1. **`QuestionAnswerAdvisor` 안에서 뭐가 일어나는지 안다**: 프레임워크가 검색 결과를 프롬프트에 어떻게 넣는지, top-K를 어떻게 결정하는지 이해하고 있으면 튜닝할 수 있다.
2. **문제가 생겼을 때 어디를 봐야 하는지 안다**: "답변이 이상하다"는 증상에서 검색 품질 문제인지, 프롬프트 문제인지, LLM 문제인지 구분할 수 있다.
3. **프레임워크를 선택할 수 있다**: 각 단계가 뭘 하는지 알면, Spring AI / LangChain / 매니지드 서비스 중 상황에 맞는 것을 고를 수 있다.

## 학습 자료

### Level 0 (개념 잡기) — 15분

- **AWS — What is RAG?**
  https://aws.amazon.com/what-is/retrieval-augmented-generation/
  > 간결한 개요. R-A-G 3단계가 각각 뭘 하는지 한 페이지로 설명.

- **Pinecone — Retrieval Augmented Generation**
  https://www.pinecone.io/learn/retrieval-augmented-generation/
  > 벡터 DB 회사의 RAG 가이드. 검색 품질이 최종 답변 품질을 좌우한다는 점 강조.

### Level 1 (구현 패턴) — 30분

- **Spring AI Reference — RAG**
  https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html
  > Spring AI 공식 RAG 문서. `QuestionAnswerAdvisor` 등 프레임워크 제공 패턴.

- **LangChain RAG Tutorial** (개념은 프레임워크 무관)
  https://python.langchain.com/docs/tutorials/rag/
  > Python이지만 RAG 파이프라인의 단계별 구성을 가장 명확하게 보여줌.

## 관련 개념

- [임베딩](CONCEPT_임베딩.md) — 검색(R)의 핵심 도구
- [코사인 유사도](CONCEPT_코사인_유사도.md) — 관련 문서를 찾는 유사도 기준
- [청킹](CONCEPT_청킹.md) — 검색 단위를 만드는 전처리
- [벡터 공간](CONCEPT_벡터_공간.md) — 검색이 일어나는 공간

## 등장한 주차

- Week 01 — Naive RAG 수동 구현 (InMemoryVectorStore)
- Week 02 — 청킹 전략 비교 + 프롬프트 엔지니어링 + 자동 평가
- Week 03 — VectorStore 추상화 + ETL 파이프라인 (Qdrant)
- Week 04 — Advanced RAG (2-Stage Retrieval, 쿼리 변환)

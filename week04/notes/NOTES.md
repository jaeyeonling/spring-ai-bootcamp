# Week 04 — 검색 개선: ReRanker, 쿼리 변환, Strategy 패턴

> **미션**: ReRanking / 쿼리 변환 / Strategy 패턴을 적용해 검색 품질을 개선하라.
> 미션 예상값은 Top-1 40% → 60% 이상이었으나, 실측 Baseline이 이미 82%였다. 판정 방식(LLM 기반)의 차이가 원인. 자세한 내용은 [EVALUATION.md](../EVALUATION.md) 참고.

## 관련 개념

- [2-Stage Retrieval](CONCEPT_2Stage_Retrieval.md)
- [ReRanking (LLM 기반 재순위화)](CONCEPT_ReRanking.md)
- [쿼리 변환](CONCEPT_쿼리_변환.md)
- [RRF (Reciprocal Rank Fusion)](CONCEPT_RRF.md)

---

## 구현 파일 구조

```
week04/src/main/java/com/jaeyeonling/week04/
├── RetrievalStrategy.java        — Strategy 인터페이스 (retrieve + getName)
├── BaselineRetrieval.java        — @Profile("baseline"): 단순 벡터 검색
├── ReRankingRetrieval.java       — @Profile("reranker"): 상위 20 후보 → LLM 재순위화
├── QueryTransformRetrieval.java  — @Profile("query-transform"): 쿼리 재작성 + 확장
└── Week04Application.java        — 진입점
```

---

## 구현 흐름

### BaselineRetrieval (기준선)

```
사용자 쿼리
    │
    ▼
SearchRequest.builder().query(query).topK(topK).build()
    │
    ▼
vectorStore.similaritySearch(request)
    │
    ▼
List<Document> (코사인 유사도 순)
```

가장 단순. 쿼리를 임베딩하고 코사인 유사도로 상위 K개를 반환한다.  
실측 Top-1 정확도: **82%** — 미션 예상값(40%)보다 훨씬 높았다. 대형 청크 구조에서 벡터 검색이 이미 충분히 잘 동작했기 때문이다.

---

### ReRankingRetrieval (2-Stage)

```
사용자 쿼리
    │
    ▼
Stage 1: vectorStore.similaritySearch(query, topK=20) → 후보 20개
    │
    ▼
Stage 2: LLM에게 각 후보의 관련성을 0.0~1.0으로 점수화 요청
    │   buildScoringPrompt(query, candidates) → 점수화 프롬프트 구성
    │   chatClient.prompt().user(prompt).call().content() → LLM 호출
    │   parseScores(response, expectedCount) → JSON 배열 파싱
    ▼
문서와 점수를 쌍으로 묶어 점수 내림차순 정렬
    │
    ▼
상위 topK개 반환
```

**점수화 프롬프트 구조**:
```
쿼리: '{query}'

각 문서의 관련성을 0.0에서 1.0으로 점수화하세요.
- 1.0: 쿼리에 대한 직접적이고 완전한 답변
- 0.7-0.9: 관련성 높음, 부분적 답변 포함
- 0.3-0.6: 같은 주제지만 직접적 답변 아님
- 0.0-0.2: 관련 없음

점수의 JSON 배열만 반환하세요. 예: [0.9, 0.3, 0.7]

문서:
[1] {문서 1 내용, 최대 500자}
[2] {문서 2 내용, 최대 500자}
...
```

**엣지 케이스**: LLM이 잘못된 JSON을 반환하거나 점수 수가 맞지 않으면, 동일 점수(0.5)를 반환하여 원래 벡터 유사도 순서를 유지한다.

---

### QueryTransformRetrieval (쿼리 변환)

```
사용자 쿼리
    │
    ▼
1단계: rewriteQuery(query)
    │   LLM 호출: "더 명확하고 구체적으로 재작성해줘"
    │   → rewrittenQuery
    ▼
2단계: expandQuery(rewrittenQuery, 3)
    │   LLM 호출: "다른 측면을 다루는 3가지 쿼리를 생성해줘"
    │   → [쿼리A, 쿼리B, 쿼리C]
    ▼
3단계: [rewrittenQuery, 쿼리A, 쿼리B, 쿼리C] 각각으로 검색
    │   → allResults: 4개의 List<Document>
    ▼
4단계: mergeAndDeduplicate(allResults, topK)
    │   빈도 기반 병합: 여러 쿼리에 등장하는 문서일수록 높은 순위
    ▼
상위 topK개 반환
```

---

## 핵심 구현 포인트

### Strategy 패턴 + Spring 프로파일

`@Profile` 애노테이션으로 활성 프로파일에 따라 하나의 `RetrievalStrategy` 빈만 등록된다.

```java
@Component
@Profile("baseline")
public class BaselineRetrieval implements RetrievalStrategy { ... }

@Component
@Profile("reranker")
public class ReRankingRetrieval implements RetrievalStrategy { ... }

@Component
@Profile("query-transform")
public class QueryTransformRetrieval implements RetrievalStrategy { ... }
```

`application.yml`에서 전환:
```yaml
spring:
  profiles:
    active: reranker  # baseline, reranker, query-transform
```

코드 변경 없이 전략을 교체할 수 있다. 이것이 "코드 변경 없이 설정으로 전략 전환" 완료 기준을 충족하는 방법.

### LLM 재순위화에서 JSON 파싱 주의사항

LLM은 요청한 JSON 배열 외에 설명 텍스트를 덧붙일 수 있다:
```
"네, 점수를 매겨드리겠습니다. [0.9, 0.3, 0.7]"
```

순수 JSON 배열만 추출하는 처리가 필요하다:
- `response` 내에서 `[` ~ `]` 구간 추출
- `ObjectMapper`로 `List<Double>` 파싱
- 파싱 실패 또는 점수 수 불일치 시 fallback: `Collections.nCopies(n, 0.5)`

### 문서 ID 기반 중복 제거

`mergeAndDeduplicate`에서 `Document.getId()`를 키로 사용한다.  
같은 문서가 여러 쿼리 결과에 나타날 때 빈도를 카운트하여 순위를 결정한다.

---

## Spring AI API 키워드

| API | 설명 |
|-----|------|
| `SearchRequest.builder().query(q).topK(k).build()` | 벡터 검색 요청 구성 |
| `vectorStore.similaritySearch(request)` | 코사인 유사도 기반 벡터 검색 실행 |
| `chatClient.prompt().user(prompt).call().content()` | LLM 호출, 텍스트 응답만 반환 |
| `@Profile("name")` | 활성 프로파일에 따라 빈 등록 여부 결정 |
| `Document.getId()` | 문서 고유 식별자 (중복 제거에 사용) |
| `Document.getText()` | 문서 내용 접근 |

---

## topK — 파라미터 vs 평가 지표

Week 01~03에서도 `topK`(또는 `TOP_K`)가 등장했지만, Week 04에서 쓰임새가 완전히 달라진다.

| 주차 | topK의 역할 | 예시 |
|------|------------|------|
| Week 01~03 | **검색 파라미터** — LLM 컨텍스트에 넣을 문서 수 | `vectorStore.search(queryVector, TOP_K)` |
| Week 04 | **평가 지표** — 정답 문서가 상위 K위 안에 있는가 | Top-1 정확도 40%, Top-5 정확도 80% |

Week 01 `ChatService`의 `TOP_K = 3`은 "검색 결과 3개를 LLM에 넘긴다"는 설정값이다. "3위 안에 정답이 있는가?"를 검증하는 코드는 없다.

Week 04에서 처음으로 K가 **평가 지표**로서 의미를 갖는다:
- **Top-1 정확도**: 정답 문서가 검색 결과 1위인 비율 → LLM이 직접 참고하는 위치
- **Top-5 정확도**: 정답 문서가 검색 결과 1~5위 안에 있는 비율 → 검색의 recall 상한선

이 둘을 분리해서 보는 이유: Week 04 미션의 전제 자체가 *"Top-5엔 있지만 Top-1이 아닌"* 상황이기 때문이다. Week 02의 `RelevancyEvaluator`(end-to-end 답변 품질)로는 이 문제를 감지할 수 없다.

---

## RAGAS 스타일 평가

**RAGAS (Retrieval Augmented Generation Assessment)** 는 RAG 시스템을 단계별로 분리해서 측정하는 평가 프레임워크다. Python 라이브러리로도 존재하지만, Week 04에서는 라이브러리를 쓰는 게 아니라 **접근 방식**을 따른다.

### RAGAS의 핵심 메트릭

| 메트릭 | 측정 대상 | 단계 |
|--------|----------|------|
| **Context Precision** | 검색된 문서 중 실제로 유용한 것의 비율 | Retrieval |
| **Context Recall** | 정답에 필요한 문서가 검색됐는가 | Retrieval |
| **Answer Relevancy** | 답변이 질문에 관련 있는가 | Generation |
| **Faithfulness** | 답변이 검색된 문서에 근거하는가 (환각 탐지) | Generation |

### Week 04에서 "RAGAS 스타일"의 의미

Week 02의 `RelevancyEvaluator`는 "최종 답변이 맞는가"만 봤다 — end-to-end 평가.  
Week 04에서는 **검색 단계를 분리**해서 측정한다.

| 평가 표의 열 | 대응하는 RAGAS 메트릭 | 설명 |
|------------|---------------------|------|
| Top-1 정확도 | Context Precision | 가장 관련 있는 문서가 1위인가 |
| Top-5 정확도 | Context Recall | 정답 문서가 상위 5개 안에 있는가 |
| 평균 관련도 | Answer Relevancy | Week 02 `RelevancyEvaluator`와 동일 |
| 평균 지연시간 | (RAGAS에 없음) | 운영 관점 추가 지표 |

Week 02 `RelevancyEvaluator`로는 *"Top-5엔 있지만 Top-1이 아닌"* 문제를 감지할 수 없다. 최종 답변이 틀려야 실패로 잡힌다. RAGAS 스타일로 검색 단계를 분리하면 **생성 전에** 검색 품질 문제를 잡을 수 있다.

---

## 정답 판정 로직 — 시도한 방식들

`containsAnswer(docs, expectedAnswer)`를 어떻게 구현할지 세 가지 방식을 시도했다.

### 시도 1: 앞 30자 문자열 포함 여부

```java
String keyPhrase = expectedAnswer.substring(0, 30).toLowerCase();
return docs.stream().anyMatch(doc -> doc.getText().toLowerCase().contains(keyPhrase));
```

**결과**: Top-1 16%, Top-5 26%

**문제**: 표현이 조금만 달라도 매칭 실패.
- `expected_answer`: `"Yes, gift cards are valid for 5 years"`
- FAQ 문서: `"Yes. Gift cards are valid for 5 years"` (쉼표 vs 마침표)

→ 문자열이 완벽히 일치해야 하므로 실제 검색 품질을 반영하지 못함.

---

### 시도 2: stop word 제거 후 키워드 전체 포함 여부

```java
List<String> keywords = // expected_answer에서 stop word 제거 후 상위 5개 단어
return keywords.stream().allMatch(doc.getText().toLowerCase()::contains);
```

**결과**: Top-1 68%, Top-5 86%

**문제**: 기준이 여전히 자의적.
- 어떤 단어를 stop word로 볼지, 몇 개를 뽑을지에 따라 수치가 크게 달라짐
- `allMatch`는 너무 엄격, `anyMatch`는 너무 관대 — 임계값 설정이 근거 없음

---

### 시도 3: 전체 문서 임베딩 유사도 (코사인 유사도 ≥ 0.7)

```java
float[] expectedVector = embeddingModel.embed(expectedAnswer);
float[] docVector = embeddingModel.embed(doc.getText());
return cosineSimilarity(expectedVector, docVector) >= 0.7;
```

**결과**: Top-1 0%, Top-5 0%

**문제**: 구조적 한계.
- `expected_answer`는 1~2문장 (짧음)
- FAQ 문서 청크는 수백~수천 자 (긺)
- 긴 문서 전체를 임베딩하면 벡터가 "평균적인 방향"을 가리키게 됨
- 짧은 `expected_answer`의 임베딩과 코사인 유사도가 구조적으로 낮아짐

→ `expected_answer` vs 전체 청크 비교는 길이 불균형으로 인해 유사도가 항상 낮음.

---

### 최종 채택: LLM 기반 판정

```java
String prompt = """
    예상 답변의 핵심 정보(숫자, 날짜, 고유명사, 핵심 사실)가 문서에 포함되어 있으면 YES,
    없으면 NO로만 답하세요.
    
    예상 답변: %s
    문서: %s
    """.formatted(expectedAnswer, context);

String response = chatClient.prompt().user(prompt).call().content().trim().toUpperCase();
return response.startsWith("YES");
```

**결과**: Top-1 82%, Top-5 92%

**채택 이유**:
- Week 02의 `RelevancyEvaluator`와 동일한 접근 방식 — 실무에서 검증된 패턴
- 표현 차이, 동의어, 문장 구조 차이를 LLM이 의미적으로 판단
- "핵심 사실 포함 여부"를 명시적으로 지시하므로 판정 기준이 명확

**비용**: 질문당 LLM 호출 최대 6회 (Top-1 판정 1회 + Top-5 판정 최대 5회)
→ 50문제 평가에 약 $0.01 수준으로 허용 가능.

---

## 테스트 결과

모든 3개 전략에서 기본 테스트 4개 모두 통과:

- [x] `strategyIsInjected` — 활성 프로파일의 전략이 정상 주입
- [x] `retrieveReturnsResults` — 유효한 쿼리에 비어있지 않은 결과 반환
- [x] `retrieveRespectsTopK` — topK 파라미터 준수
- [x] `retrievedDocumentsHaveContent` — 검색된 문서에 비어있지 않은 내용

---

## 평가 결과

| 전략           | Top-1 정확도 | Top-5 정확도 | 평균 지연시간 (ms) |
|----------------|:----------:|:----------:|:----------------:|
| Baseline        | 82.0% (41/50) | 92.0% (46/50) | 155 ms        |
| ReRanker        | 56.0% (28/50) | 96.0% (48/50) | 2,709 ms      |
| QueryTransform  | 74.0% (37/50) | 96.0% (48/50) | 2,534 ms      |

예상과 다른 결과: **Baseline이 Top-1에서 압도적 1위**다. ReRanker와 QueryTransform은 지연시간을 16~17배 늘리면서 Top-1을 오히려 낮췄다.

→ 자세한 분석: [EVALUATION.md](../EVALUATION.md)

---

## 배운 것 / 느낀 것

1. **고급 전략이 항상 더 좋지 않다.** 파이프라인 전체의 일관성이 중요하다. 대형 청크에서 ReRanker는 역효과다.
2. **LLM 기반 평가는 강력하지만 비싸다.** 50문제 × 5회 판정 = 최대 250 LLM 호출. 빠른 이터레이션엔 불리하다.
3. **topK는 두 가지 뜻을 갖는다.** 검색 파라미터(몇 개 가져올지)와 평가 지표(Top-K 정확도)가 다르다. 문맥을 잘 구분해야 한다.
4. **청킹이 검색 전략의 효과를 결정한다.** Q&A 단위 청킹이었다면 ReRanker가 더 좋은 결과를 냈을 것이다.

---

## 다음 주차로 연결

Week 05에서 이어지는 것:
- QueryTransform 전략은 LLM 호출이 많아 지연시간이 크게 증가 → Week 05 관측성(Micrometer, OTel)으로 어느 스팬에서 병목이 생기는지 측정
- 전략별 지연시간 차이를 Jaeger 트레이싱으로 시각화

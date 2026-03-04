# 쿼리 변환 (Query Transformation)

## 한 문장 정의

> 벡터 검색에 보내기 전에 사용자의 원본 쿼리를 개선하여 검색 품질을 높이는 기법.

## 왜 필요한가

사용자는 "좋은 검색 쿼리"를 쓰지 않는다.

| 사용자 입력 | 실제 의도 | 문제 |
|------------|----------|------|
| "환불" | "환불 정책은 어떻게 되고 어떻게 요청하나요?" | 너무 짧아 임베딩이 방향을 못 잡음 |
| "안 돼요" | "주문이 배송 완료인데 못 받았습니다" | 맥락 없음 |
| "반품 기간" | "return period" FAQ를 찾아야 함 | cross-language 매칭 실패 |
| "취소 배송 결제" | 3개의 별개 질문 | 임베딩이 어느 주제에도 정확히 매칭 안 됨 |

코사인 유사도는 쿼리와 문서의 임베딩이 가까울 때만 작동한다.  
쿼리가 모호하면 임베딩 자체가 "아무데나" 위치하게 되어 관련 문서를 놓친다.

**핵심 통찰**: [2-Stage Retrieval](CONCEPT_2Stage_Retrieval.md)의 Stage 2(ReRanking)는 이미 검색된 후보 안에서만 순위를 바꾼다. **Stage 1에서 아예 후보에 들지 못한 문서는 ReRanking으로도 복구 불가**. 쿼리 변환은 Stage 1 이전에 작용하여 더 좋은 후보 풀을 확보한다.

## 두 가지 기법

### 1. 쿼리 재작성 (Query Rewriting)

원본 쿼리를 더 명확하고 구체적인 형태로 변환한다.

```
원본:    "환불"
재작성:  "초록 코퍼레이션 환불 정책과 환불 요청 절차는 무엇인가요?"
```

**왜 효과가 있나**: 재작성된 쿼리는 임베딩 공간에서 더 구체적인 위치를 가진다. "환불"은 방향이 모호하지만, "환불 정책과 환불 요청 절차"는 FAQ 문서의 임베딩과 더 가까운 방향을 가리킨다.

구현 방식:
```java
String prompt = """
    다음 검색 쿼리를 더 명확하고 구체적으로 재작성해 주세요.
    재작성된 쿼리만 반환하세요.
    
    원본 쿼리: %s
    """.formatted(originalQuery);

String rewritten = chatClient.prompt()
    .user(prompt)
    .call()
    .content()
    .trim();
```

**비용**: LLM 1회 호출. gpt-4o-mini 기준 ~100 토큰 입출력 ≈ $0.00003.

### 2. 멀티 쿼리 확장 (Multi-Query Expansion)

하나의 쿼리를 여러 관점의 쿼리로 확장한 뒤, 각각으로 검색하고 결과를 병합한다.

```
원본:    "반품은 어떻게 하나요?"
확장 1:  "반품 요청 절차와 필요한 조건"
확장 2:  "반품 가능 기간과 상품 상태 요건"
확장 3:  "반품 시 환불 처리 방식과 소요 시간"
```

**왜 효과가 있나**: 단일 쿼리 임베딩은 의미 공간의 한 점만 찍는다. 여러 쿼리는 관련 문서 주변의 여러 점을 찍어 **recall을 높인다**. 특히 cross-language 상황에서:

```
"반품 기간" → 임베딩 공간에서 "return period" 문서와 거리 0.65
"return window and eligible period" → 같은 문서와 거리 0.92
```

쿼리 중 하나라도 관련 문서에 가까우면 검색에 성공한다.

### 결합: Rewrite → Expand → Search → Merge

```
원본 쿼리
    │
    ▼
┌─────────────────────┐
│ 1. 쿼리 재작성       │  ← LLM 호출 1회
│    "환불" → "환불 정책│
│    과 요청 절차"      │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│ 2. 멀티 쿼리 확장    │  ← LLM 호출 1회
│    → 쿼리 A, B, C    │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│ 3. 각 쿼리로 검색    │  ← 벡터 검색 4회 (원본 포함)
│    → 결과 A, B, C, D │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│ 4. 결과 병합 + 중복  │  ← RRF
│    제거              │
└──────────┬──────────┘
           │
           ▼
      최종 top-K
```

## 결과 병합 전략

여러 쿼리의 검색 결과를 합칠 때, 단순히 합치면 중복이 생긴다. 병합 전략이 필요하다.

### 단순 빈도 기반

가장 간단한 방식: 여러 쿼리 결과에 자주 등장하는 문서가 더 관련성이 높다고 판단.

```java
Map<String, Integer> frequency = new LinkedHashMap<>();
for (List<Document> results : allResults) {
    for (Document doc : results) {
        frequency.merge(doc.getId(), 1, Integer::sum);
    }
}
// frequency 내림차순 정렬 → 상위 K개
```

### Reciprocal Rank Fusion (RRF)

빈도뿐 아니라 **각 결과에서의 순위**도 반영. 상세는 [RRF](CONCEPT_RRF.md) 참조.

## Spring AI 1.1.x의 표준 API

Spring AI는 `spring-ai-rag` 모듈에 쿼리 변환 클래스를 제공한다:

```java
// 재작성
RewriteQueryTransformer transformer = RewriteQueryTransformer.builder()
    .chatClientBuilder(chatClientBuilder)
    .build();
Query rewritten = transformer.transform(new Query(originalQuery));

// 확장
MultiQueryExpander expander = MultiQueryExpander.builder()
    .chatClientBuilder(chatClientBuilder)
    .build();
List<Query> expanded = expander.expand(rewritten);
```

내부적으로 ChatClient를 호출하므로, 직접 ChatClient를 쓰는 것과 동일한 효과다.  
Week 04에서는 **직접 ChatClient를 호출하는 방식으로 구현**하여 내부 동작을 이해하고, 이후 필요하면 표준 API로 전환할 수 있다.

## 비용과 지연시간

| 단계 | LLM 호출 | 임베딩 호출 | 추가 지연 (예상) |
|------|---------|-----------|-----------------|
| 재작성 | 1회 (~100 토큰) | 0 | ~300ms |
| 확장 (3개) | 1회 (~200 토큰) | 0 | ~300ms |
| 검색 (4개 쿼리) | 0 | 4회 | ~500ms (임베딩 latency 주도) |
| **합계** | **2회** | **4회** | **~1,100ms** |

Baseline 대비 약 3-4배 느려진다. 이것이 Week 05 관측성이 필요한 이유로 자연스럽게 연결된다.

## 학습자가 자주 하는 질문

### "이러면 LLM 호출이 너무 많은 거 아닌가?"

맞다. 프로덕션에서는 보통 재작성 또는 확장 중 하나만 선택한다.  
둘 다 하는 것은 실험/평가 단계에서 효과를 비교하기 위함.

어떤 걸 선택해야 하나:
- 사용자 쿼리가 **짧고 모호**한 경향 → 재작성이 효과적
- 사용자 쿼리가 **복잡하고 다면적**인 경향 → 확장이 효과적
- **cross-language** 문제가 심한 경우 → 확장 + 영어 변환이 효과적

### "재작성하면 원래 의도가 변질되지 않나?"

가능성 있다. 대응 방법:
1. 재작성된 쿼리와 원본 쿼리 **둘 다** 검색에 사용
2. 재작성 프롬프트에 "의미를 변경하지 말고 구체화만 하세요" 명시
3. 실제 평가 데이터로 재작성 전/후 정확도 비교

### "우리 FAQ가 영어인데 한글 쿼리를 영어로 바꾸면 되지 않나?"

Week 02에서 확인한 cross-language 문제의 가장 직접적인 해결책이다.  
재작성 프롬프트에 "영어로 재작성하세요"를 추가하면 사실상 번역 + 재작성이 된다.  
단, 이렇게 하면 **언어 중립적 임베딩의 능력을 테스트하는 교육 목적에는 맞지 않을 수 있다**.

## 관련 개념

- [2-Stage Retrieval](CONCEPT_2Stage_Retrieval.md) — 쿼리 변환은 Stage 1 이전에 작용
- [RRF](CONCEPT_RRF.md) — 멀티 쿼리 결과 병합 전략

## 등장한 주차

- Week 04 — QueryTransformRetrieval 구현

## 학습 자료

### Level 0 (개념 잡기) — 10분

- **LangChain — Query Transformation**  
  https://python.langchain.com/docs/concepts/retrieval/#query-transformation  
  > Query rewriting, step-back prompting, multi-query의 개념을 간결하게 정리.

### Level 1 (구현 패턴) — 20분

- **Spring AI Reference — Pre-Retrieval**  
  https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html  
  > `RewriteQueryTransformer`, `MultiQueryExpander` 공식 문서.

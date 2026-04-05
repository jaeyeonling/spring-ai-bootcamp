# M2 미션: 검색 품질

## Stage 1에서 겪은 벽

M1으로 Qdrant에 3,100건을 적재했습니다. 그런데 여전히 이런 일이 생깁니다:

```
질문: "마켓플레이스 상품도 14일 반품 가능한가요?"

검색 Top-3:
1. [상담로그] "반품은 7일이에요" (신입 상담사 오답)
2. [정책 v1] "return within 7 business days" (구버전)
3. [FAQ] "Marketplace items follow the seller's policy" ← 정답
```

정답이 3위입니다. 코사인 유사도만으로는 3,000건 노이즈 속에서 정답을 1위로 올리기 어렵습니다.

---

## 미션

검색 전략을 개선하여 **Baseline 대비 Top-1 정확도를 +15%p 이상** 올리세요.

단, 개선 과정을 수치로 증명해야 합니다.
"더 나아진 것 같다"가 아니라 "기존 38% → 현재 54%"처럼 측정하세요.

### 완료 기준

```
[ ] Baseline 정확도 측정 (M1 완료 직후 수치)
[ ] 전략 적용 후 정확도 재측정
[ ] Top-1 정확도 +15%p 이상 향상
[ ] 개선 과정을 EVALUATION.md에 수치로 기록
```

---

## 개선 전략 메뉴

한 번에 모두 적용하지 마세요. 하나씩 적용하고 측정하세요.
어떤 전략이 얼마나 효과가 있는지 직접 확인하는 것이 목표입니다.

### 전략 1: 메타데이터 필터링 (시작점으로 권장)

`status: current` 문서만 검색하면 구버전 노이즈가 제거됩니다.

```java
Filter.Expression filter = new Filter.Expression(
    Filter.ExpressionType.EQ,
    new Filter.Key("status"),
    new Filter.Value("current")
);
SearchRequest request = SearchRequest.query(query)
    .withFilterExpression(filter)
    .withTopK(10);
```

### 전략 2: ReRanker

벡터 검색으로 후보 20개를 뽑고, LLM이 질문과의 관련성을 다시 채점합니다.

```
[벡터 검색] top-20 후보 → [LLM 채점] 관련성 0-10 → [정렬] top-5 반환
```

Stage 1의 데이터(FAQ 24개)에서는 ReRanker가 역효과를 냈습니다.
지금 환경(3,100건 + 노이즈)에서는 어떨지 직접 확인하세요.

### 전략 3: 쿼리 변환 (Query Rewriting)

"걍 환불해주세요 ㅠㅠ" → "refund policy and process"로 재작성합니다.

```java
String rewrittenQuery = chatClient.prompt()
    .system("""
        다음 고객 질문을 영어 FAQ 검색에 최적화된 형태로 재작성하세요.
        구어체, 이모티콘, 불필요한 표현을 제거하고 핵심 키워드 중심으로.
        재작성된 질문만 출력하세요.
        """)
    .user(originalQuery)
    .call()
    .content();
```

### 전략 4: 하이브리드 검색

주문번호(`20240815-042`)나 상품명 같은 고유명사는 벡터 유사도로 매칭이 잘 안 됩니다.
벡터(의미) + BM25(키워드)를 결합하면 이런 케이스를 잡을 수 있습니다.

---

## 평가 기반 개선 사이클

```
1. 현재 정확도 측정
2. 실패한 질문 분석 → 왜 틀렸는가?
3. 그 실패를 해결하는 전략 선택
4. 전략 적용 후 정확도 재측정
5. 개선폭이 기대만큼인가? → 반복
```

이 사이클 자체가 이 모듈의 핵심 학습입니다.

---

## 힌트

| 힌트 | 이럴 때 열어보세요 |
|------|-------------------|
| `HINT_01.md` | 메타데이터 필터를 어떻게 설정하는지 모를 때 |
| `HINT_02.md` | ReRanker 구현이 막힐 때, 또는 효과가 예상과 다를 때 |
| `HINT_03.md` | 쿼리 변환 + 하이브리드 검색으로 넘어가고 싶을 때 |

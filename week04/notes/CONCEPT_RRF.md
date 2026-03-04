# Reciprocal Rank Fusion (RRF)

## 한 문장 정의

> 여러 검색 결과 목록을 하나로 합칠 때, 각 목록에서의 순위를 기반으로 통합 점수를 계산하는 알고리즘.

## 왜 필요한가

[쿼리 변환](CONCEPT_쿼리_변환.md)에서 멀티 쿼리 확장을 사용하면 여러 검색 결과가 나온다. 이것을 하나의 순위 목록으로 합쳐야 한다.

가장 단순한 방법은 "여러 결과에 자주 등장하는 문서를 높게"인데, 이 방식은 **순위 정보를 버린다**. 쿼리 A에서 1위인 문서와 10위인 문서가 동일하게 "1번 등장"으로 카운트된다.

RRF는 순위 정보를 보존하면서 여러 결과를 융합한다.

## 공식

### Level 0 — 직감

여러 검색 목록에서 순위가 높을수록, 그리고 여러 목록에 자주 나올수록 최종 점수가 높다. "여러 사람이 동의하는 답이 좋은 답"이라는 직감.

### Level 1 — 공식

```
RRF_score(d) = Σ  1 / (k + rank(d, q_i))
              q_i
```

- `d`: 문서
- `q_i`: i번째 쿼리의 검색 결과
- `rank(d, q_i)`: 쿼리 `q_i`의 결과에서 문서 `d`의 순위 (1부터 시작)
- `k`: 스무딩 상수 (보통 60). 순위 1위의 지배력을 완화
- 합산은 문서 `d`가 등장한 모든 결과 목록에 대해 수행

**예시**:

3개 쿼리로 검색, k=60:

| 문서 | 쿼리 A 순위 | 쿼리 B 순위 | 쿼리 C 순위 | RRF 점수 |
|------|:----------:|:----------:|:----------:|:--------:|
| doc1 | 1 | 3 | - | 1/61 + 1/63 = 0.0323 |
| doc2 | 2 | 1 | 2 | 1/62 + 1/61 + 1/62 = 0.0487 |
| doc3 | - | 2 | 1 | 1/62 + 1/61 = 0.0325 |

최종 순위: doc2 > doc3 > doc1

doc2가 최고 순위: 3개 목록 모두에 등장하고 순위도 높다.  
doc3이 doc1보다 높은 이유: doc1은 1위가 있지만 2개 목록에만 등장. doc3도 2개지만 1위가 있고 합산이 근소하게 높다.

### k=60의 의미

k가 크면 순위 차이의 영향이 줄어든다:
- k=0: 1위 점수 1.0, 2위 0.5 → 1위가 압도적
- k=60: 1위 점수 0.0164, 2위 0.0161 → 차이 미미

k=60은 원 논문(Cormack et al., 2009)에서 실험적으로 가장 robust한 값으로 결정됨. 대부분의 구현에서 이 값을 그대로 사용.

## 구현

```java
private List<Document> fuseWithRRF(List<List<Document>> resultSets, int topK) {
    final int k = 60;
    Map<String, Double> rrfScores = new LinkedHashMap<>();
    Map<String, Document> docMap = new LinkedHashMap<>();

    for (List<Document> results : resultSets) {
        for (int rank = 0; rank < results.size(); rank++) {
            Document doc = results.get(rank);
            String id = doc.getId();
            docMap.putIfAbsent(id, doc);
            rrfScores.merge(id, 1.0 / (k + rank + 1), Double::sum);
        }
    }

    return rrfScores.entrySet().stream()
        .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
        .limit(topK)
        .map(e -> docMap.get(e.getKey()))
        .toList();
}
```

## 단순 빈도 카운트와의 비교

| | 빈도 카운트 | RRF |
|-|-----------|-----|
| 순위 정보 | 무시 | 반영 |
| 구현 복잡도 | 매우 간단 | 간단 |
| 정확도 | 나쁘지 않음 | 더 좋음 |
| 언제 사용 | 빠른 프로토타입 | 프로덕션 |

Week 04 스켈레톤에서는 빈도 카운트를 제시하지만, RRF로 교체하면 추가 정확도 향상을 기대할 수 있다. 둘 다 구현해보고 비교하는 것이 교육적으로 가치 있다.

## 하이브리드 검색에서의 RRF

RRF의 원래 용도는 **서로 다른 검색 방식의 결과를 합치는 것**이다:

```
벡터 검색 결과 (의미적 유사도)  ─┐
                                ├── RRF ──→ 최종 순위
키워드 검색 결과 (BM25)        ─┘
```

이것이 "하이브리드 검색"의 핵심. 벡터 검색은 의미를 잡고, BM25는 정확한 키워드 매칭을 잡는다. 둘의 점수 척도가 완전히 다르기 때문에 (코사인 0~1 vs BM25 0~∞) 점수를 직접 비교할 수 없고, RRF의 순위 기반 융합이 이를 해결한다.

Week 04 미션에서는 멀티 쿼리의 결과 병합에 RRF를 사용하지만, 동일한 알고리즘이 하이브리드 검색에도 그대로 적용된다.

## 학습자가 자주 하는 질문

### "k=60이 아닌 다른 값을 쓰면?"

실험해볼 가치가 있지만, 대부분의 벤치마크에서 k=60이 robust하다. k를 작게 하면(예: 1) 순위 1위의 영향력이 압도적이 되고, 크게 하면(예: 1000) 모든 순위가 거의 동일해져 빈도 카운트에 수렴한다.

### "문서가 어떤 결과에도 안 나오면?"

RRF 점수 0으로 처리 — 최종 목록에 포함되지 않는다. 이것은 올바른 동작: 어떤 쿼리로도 검색에 잡히지 않은 문서는 관련 없을 가능성이 높다.

## 관련 개념

- [쿼리 변환](CONCEPT_쿼리_변환.md) — 멀티 쿼리 확장 후 결과 병합에 RRF 사용
- [2-Stage Retrieval](CONCEPT_2Stage_Retrieval.md) — RRF는 Stage 1의 결과 병합에 적용

## 등장한 주차

- Week 04 — QueryTransformRetrieval의 mergeAndDeduplicate 구현

## 학습 자료

### Level 0 (개념 잡기) — 5분

- **Weaviate — Hybrid Search Explained**  
  https://weaviate.io/blog/hybrid-search-explained  
  > RRF가 하이브리드 검색에서 어떻게 사용되는지 시각적으로 설명.

### Level 1 (원 논문) — 15분

- **Cormack, Clarke, Butt (2009). Reciprocal Rank Fusion outperforms Condorcet and individual Rank Learning Methods**  
  https://dl.acm.org/doi/10.1145/1571941.1572114  
  > 2페이지짜리 짧은 논문. RRF가 왜 단순하면서도 효과적인지 실험 결과 포함.

# 힌트 02: ReRanker — LLM이 후보 문서를 다시 채점

코사인 유사도만으로는 부족합니다. 상위 20개 후보를 뽑고 LLM이 다시 순위를 매깁니다.

## 왜 ReRanker가 필요한가

코사인 유사도는 "의미적 유사성"을 측정합니다.
하지만 "이 문서가 이 질문에 실제로 답이 되는가"는 다른 문제입니다.

```
질문: "마켓플레이스 상품 반품 가능한가요?"

코사인 유사도 Top-3:
1. [상담로그] "반품하고 싶어요 ㅠㅠ" (유사도 0.91) — 관련 있지만 답이 없음
2. [정책 v1] "7일 이내 반품" (유사도 0.89) — 구버전, 마켓플레이스 아님
3. [FAQ] "마켓플레이스는 셀러 정책 따름" (유사도 0.85) ← 실제 정답

ReRanker 적용 후:
1. [FAQ] "마켓플레이스는 셀러 정책 따름" (관련성 9/10)
```

## 구현

```java
@Service
public class ReRankingRetrieval implements RetrievalStrategy {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    @Override
    public List<Document> retrieve(String question) {
        // 1단계: 벡터 검색으로 후보 20개
        List<Document> candidates = vectorStore.similaritySearch(
            SearchRequest.query(question).withTopK(20)
        );

        // 2단계: LLM이 관련성 채점
        return rerank(question, candidates).stream()
            .sorted(Comparator.comparingDouble(ScoredDoc::score).reversed())
            .limit(5)
            .map(ScoredDoc::document)
            .toList();
    }

    private List<ScoredDoc> rerank(String question, List<Document> candidates) {
        String prompt = """
            질문: %s

            다음 문서들의 관련성을 0-10으로 채점하세요.
            JSON 배열로 응답하세요: [{"index": 0, "score": 8}, ...]

            문서들:
            %s
            """.formatted(
                question,
                IntStream.range(0, candidates.size())
                    .mapToObj(i -> "[" + i + "] " + candidates.get(i).getText())
                    .collect(Collectors.joining("\n\n"))
            );

        // 구조화 출력으로 파싱
        List<RerankScore> scores = chatClient.prompt()
            .user(prompt)
            .call()
            .entity(new ParameterizedTypeReference<List<RerankScore>>() {});

        return scores.stream()
            .map(s -> new ScoredDoc(candidates.get(s.index()), s.score()))
            .toList();
    }

    record RerankScore(int index, double score) {}
    record ScoredDoc(Document document, double score) {}
}
```

## 기존 커리큘럼에서 ReRanker가 역효과를 낸 이유

이전 커리큘럼(Week 04)에서는 ReRanker가 정확도를 **낮췄습니다** (82% → 56%).

이유: FAQ 24개, 17개 청크 → 코사인 유사도만으로 이미 정확도 82%.
후보 20개를 뽑으면 전체 데이터의 85%를 가져오는 것과 같음 → LLM 채점이 오히려 노이즈.

지금 환경(3,100건)에서는 달라야 합니다. 직접 측정해보세요.

## 비용 고려

ReRanker는 LLM을 추가로 호출합니다 (후보 20개 채점).
응답 시간과 비용이 늘어납니다. 측정하세요:

```
Baseline: 평균 응답 200ms, 요청당 $0.001
ReRanker: 평균 응답 ?ms, 요청당 $?

정확도 향상폭 vs 비용/지연 증가폭을 비교하여 트레이드오프를 문서화하세요.
```

package com.jaeyeonling.week04;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 쿼리 변환 검색 전략 — 검색 전에 쿼리를 개선합니다.
 *
 * ChatClient를 직접 사용해 모호하거나 짧은 쿼리를
 * 더 구체적이고 검색에 친화적인 버전으로 재작성하고,
 * 단일 쿼리를 다양한 측면을 포함하는 여러 쿼리로 확장합니다.
 *
 * Spring AI 1.1.x부터는 org.springframework.ai.rag 패키지의
 * RewriteQueryTransformer, MultiQueryExpander를 직접 사용할 수 있습니다.
 * 이 구현은 ChatClient를 직접 호출해 동일한 효과를 냅니다.
 * week04/hints/HINT_02.md에서 두 방식을 모두 참고하세요.
 */
@Component
@Profile("query-transform")
public class QueryTransformRetrieval implements RetrievalStrategy {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    public QueryTransformRetrieval(
            VectorStore vectorStore,
            ChatClient.Builder chatClientBuilder) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public List<Document> retrieve(String query, int topK) {
        // 1단계 — 명확성과 구체성을 위해 쿼리를 재작성
        String rewrittenQuery = rewriteQuery(query);

        // 2단계 — 재작성된 쿼리를 여러 쿼리로 확장
        List<String> expandedQueries = new ArrayList<>(expandQuery(rewrittenQuery, 3));
        expandedQueries.add(0, rewrittenQuery); // 재작성된 쿼리도 포함

        // 3단계 — 확장된 각 쿼리로 검색
        List<List<Document>> allResults = new ArrayList<>();
        for (String q : expandedQueries) {
            List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder().query(q).topK(topK).build()
            );
            allResults.add(results);
        }

        // 4단계 — 결과를 병합하고 중복을 제거
        return mergeAndDeduplicate(allResults, topK);
    }

    /**
     * 더 나은 검색을 위해 LLM을 사용해 쿼리를 재작성합니다.
     *
     * @param query 원본 사용자 쿼리
     * @return 재작성된, 더 구체적인 쿼리
     */
    private String rewriteQuery(String query) {
        return chatClient.prompt()
                .user("""
                        다음 쿼리를 더 구체적이고 검색에 친화적으로 재작성하세요.
                        재작성된 쿼리만 반환하고, 다른 것은 포함하지 마세요.
                        원본 쿼리: %s
                        """.formatted(query))
                .call()
                .content()
                .trim();
    }

    /**
     * LLM을 사용해 쿼리를 여러 변형으로 확장합니다.
     *
     * @param query  확장할 쿼리
     * @param count  생성할 변형 수
     * @return 쿼리 변형 목록
     */
    private List<String> expandQuery(String query, int count) {
        String response = chatClient.prompt()
                .user("""
                        이 질문의 다양한 측면을 다루는 %d가지 검색 쿼리를 생성하세요.
                        쿼리만 반환하고, 한 줄에 하나씩, 번호나 추가 텍스트 없이.
                        질문: %s
                        """.formatted(count, query))
                .call()
                .content()
                .trim();

        return Arrays.stream(response.split("\n"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .limit(count)
                .toList();
    }

    /**
     * 여러 검색의 결과를 병합하고 문서 ID로 중복을 제거합니다.
     * 더 많은 결과에 나타나는 문서가 더 높은 순위를 가집니다.
     *
     * @param resultSets 쿼리당 하나의 결과 목록
     * @param topK       반환할 문서 수
     * @return 병합되고 중복 제거된 결과
     */
    private List<Document> mergeAndDeduplicate(List<List<Document>> resultSets, int topK) {
        Map<String, Document> seen = new LinkedHashMap<>();
        Map<String, Integer> frequency = new LinkedHashMap<>();

        for (List<Document> results : resultSets) {
            for (Document doc : results) {
                String id = doc.getId();
                seen.putIfAbsent(id, doc);
                frequency.merge(id, 1, Integer::sum);
            }
        }

        return frequency.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(topK)
                .map(e -> seen.get(e.getKey()))
                .toList();
    }
}

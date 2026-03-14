package com.jaeyeonling.week10;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Week 04 핵심 개념: QueryTransform + LLM ReRanker 파이프라인.
 *
 * FaqSearchTool에서 LLM 의존 로직만 분리한 서비스.
 * FaqSearchTool은 @Tool 등록을 위해 VectorStore만 의존해야 하므로,
 * ChatClient가 필요한 이 로직을 별도 빈으로 추출하여 순환 의존성을 제거한다.
 *
 * 의존성 그래프:
 *   ToolCallbackProvider → FaqSearchTool → VectorStore          (순환 없음)
 *   FaqReranker          → VectorStore + ChatClient             (독립적)
 *   ChatService          → FaqReranker (소스 추적용 고급 검색)
 */
@Service
public class FaqReranker {

    private static final Logger log = LoggerFactory.getLogger(FaqReranker.class);
    private static final int CANDIDATE_COUNT = 10;
    private static final int MAX_CONTENT_LENGTH = 500;

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public FaqReranker(VectorStore vectorStore, ChatClient.Builder chatClientBuilder) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * QueryTransform + ReRanker 파이프라인으로 관련 문서를 검색합니다.
     * ChatService.chat()에서 소스 추적 및 FaqOrchestratorService 컨텍스트 구성에 사용됩니다.
     *
     * 파이프라인: 쿼리 재작성 → 다중 쿼리 확장 → 후보 병합 → LLM 재순위 → Top-K 반환
     */
    public List<Document> retrieve(String query, int topK) {
        // 1단계: QueryTransform — 쿼리 재작성
        String rewrittenQuery = rewriteQuery(query);
        log.debug("[QueryTransform] '{}' → '{}'", query, rewrittenQuery);

        // 2단계: 다중 쿼리 확장 및 검색
        List<String> queries = new ArrayList<>();
        queries.add(rewrittenQuery);
        queries.addAll(expandQuery(rewrittenQuery, 2));

        List<List<Document>> allResults = queries.stream()
                .map(q -> vectorStore.similaritySearch(
                        SearchRequest.builder().query(q).topK(CANDIDATE_COUNT).build()))
                .toList();

        // 3단계: 중복 제거 병합
        List<Document> candidates = mergeAndDeduplicate(allResults, CANDIDATE_COUNT);
        log.debug("[QueryTransform] 후보 {}개 병합", candidates.size());

        if (candidates.isEmpty()) {
            return candidates;
        }

        // 4단계: LLM ReRanker — 관련성 점수로 재순위
        return rerank(query, candidates, topK);
    }

    // ── QueryTransform 로직 ──────────────────────────────────────────────────

    private String rewriteQuery(String query) {
        try {
            return chatClient.prompt()
                    .user("""
                            다음 쿼리를 더 구체적이고 검색에 친화적으로 재작성하세요.
                            재작성된 쿼리만 반환하고, 다른 것은 포함하지 마세요.
                            원본 쿼리: %s
                            """.formatted(query))
                    .call()
                    .content()
                    .trim();
        } catch (Exception e) {
            log.warn("[QueryTransform] 쿼리 재작성 실패, 원본 사용: {}", e.getMessage());
            return query;
        }
    }

    private List<String> expandQuery(String query, int count) {
        try {
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
        } catch (Exception e) {
            log.warn("[QueryTransform] 쿼리 확장 실패: {}", e.getMessage());
            return List.of();
        }
    }

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

    // ── ReRanker 로직 ────────────────────────────────────────────────────────

    private List<Document> rerank(String query, List<Document> candidates, int topK) {
        List<Double> scores = rerankWithLlm(query, candidates);

        List<ScoredDocument> scored = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            scored.add(new ScoredDocument(candidates.get(i), scores.get(i)));
        }

        return scored.stream()
                .sorted(Comparator.comparingDouble(ScoredDocument::score).reversed())
                .limit(topK)
                .map(ScoredDocument::document)
                .toList();
    }

    private List<Double> rerankWithLlm(String query, List<Document> candidates) {
        StringBuilder sb = new StringBuilder();
        sb.append("쿼리: '").append(query).append("'\n\n");
        sb.append("각 문서의 관련성을 0.0에서 1.0으로 점수화하세요.\n");
        sb.append("- 1.0: 쿼리에 대한 직접적이고 완전한 답변\n");
        sb.append("- 0.7-0.9: 관련성 높음, 부분적 답변 포함\n");
        sb.append("- 0.3-0.6: 같은 주제지만 직접적 답변 아님\n");
        sb.append("- 0.0-0.2: 관련 없음\n\n");
        sb.append("점수의 JSON 배열만 반환하세요. 예시: [0.9, 0.3, 0.7]\n\n");
        sb.append("문서:\n");

        for (int i = 0; i < candidates.size(); i++) {
            String content = candidates.get(i).getText();
            if (content.length() > MAX_CONTENT_LENGTH) {
                content = content.substring(0, MAX_CONTENT_LENGTH) + "...";
            }
            sb.append("[").append(i + 1).append("] ").append(content).append("\n\n");
        }

        try {
            String response = chatClient.prompt()
                    .user(sb.toString())
                    .call()
                    .content();

            int start = response.indexOf('[');
            int end = response.lastIndexOf(']');
            if (start == -1 || end == -1) {
                throw new IllegalArgumentException("JSON 배열을 찾을 수 없음");
            }

            List<Double> scores = objectMapper.readValue(
                    response.substring(start, end + 1),
                    new TypeReference<>() {});

            if (scores.size() != candidates.size()) {
                log.warn("[ReRanker] 점수 수 불일치: 예상 {}, 실제 {}. 원래 순서 유지",
                        candidates.size(), scores.size());
                return Collections.nCopies(candidates.size(), 0.5);
            }

            return scores;
        } catch (Exception e) {
            log.warn("[ReRanker] 점수 파싱 실패: {}. 원래 순서 유지", e.getMessage());
            return Collections.nCopies(candidates.size(), 0.5);
        }
    }

    private record ScoredDocument(Document document, double score) {}
}

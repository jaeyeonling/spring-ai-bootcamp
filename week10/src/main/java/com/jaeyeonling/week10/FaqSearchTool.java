package com.jaeyeonling.week10;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 고급 검색 파이프라인을 LLM 툴로 노출합니다.
 *
 * Week 04 핵심 개념을 통합:
 * 1. QueryTransform — 모호한 쿼리를 재작성하고 다중 관점으로 확장
 * 2. ReRanker — LLM이 후보 문서의 관련성을 점수화하여 재순위
 *
 * 파이프라인: 쿼리 재작성 → 다중 검색 → 후보 병합 → LLM 재순위 → Top-K 반환
 *
 * Week 04 평가에서 배운 것:
 * - FAQ처럼 청크가 크면 ReRanker의 500자 잘라냄이 역효과
 * - Week 10은 청크 크기를 400으로 줄여 ReRanker 효과 극대화
 */
@Service
public class FaqSearchTool {

    private static final Logger log = LoggerFactory.getLogger(FaqSearchTool.class);
    private static final int CANDIDATE_COUNT = 10;
    private static final int MAX_CONTENT_LENGTH = 500;

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public FaqSearchTool(VectorStore vectorStore, @Lazy ChatClient.Builder chatClientBuilder) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * FAQ와 회사 정책 문서를 고급 검색 파이프라인으로 검색합니다.
     * QueryTransform + ReRanker 적용 (Week 04 통합).
     */
    @Tool(description = "회사 정책, 환불 규정, 배송 세부사항, 계정 관리 및 기타 일반적인 질문에 대한 " +
                         "정보를 위해 FAQ와 회사 정책 문서를 검색합니다. " +
                         "특정 주문 상태나 매출 수치에 관한 질문이 아닐 때 사용하세요.")
    public String searchFaq(
            @ToolParam(description = "찾을 정보를 설명하는 검색 쿼리") String query) {

        log.info("툴 호출됨: searchFaq(query={})", query);

        List<Document> results = retrieveWithAdvancedPipeline(query, 3);

        if (results.isEmpty()) {
            return "검색에서 관련 FAQ 항목을 찾을 수 없습니다: " + query;
        }

        return results.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    /**
     * 소스 ID와 함께 FAQ를 검색합니다 (ChatService에서 sources 필드 구성 시 사용).
     */
    public List<Document> searchWithSources(String query, int topK) {
        return retrieveWithAdvancedPipeline(query, topK);
    }

    // ── Week 04 통합: QueryTransform + ReRanker 파이프라인 ─────────────────

    /**
     * 고급 검색 파이프라인:
     * 1. 쿼리 재작성 (QueryTransform)
     * 2. 다중 쿼리로 확장 후 병합
     * 3. LLM ReRanker로 재순위
     */
    private List<Document> retrieveWithAdvancedPipeline(String query, int topK) {
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

        // 3단계: 중복 제거 병합 (QueryTransform 패턴)
        List<Document> candidates = mergeAndDeduplicate(allResults, CANDIDATE_COUNT);
        log.debug("[QueryTransform] 후보 {}개 병합", candidates.size());

        if (candidates.isEmpty()) {
            return candidates;
        }

        // 4단계: LLM ReRanker — 관련성 점수로 재순위
        return rerank(query, candidates, topK);
    }

    // ── Week 04: QueryTransform 로직 ──────────────────────────────────────

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

    // ── Week 04: ReRanker 로직 ─────────────────────────────────────────────

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

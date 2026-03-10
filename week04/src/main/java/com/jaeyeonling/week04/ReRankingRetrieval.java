package com.jaeyeonling.week04;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 재순위화 검색 전략 — 광범위하게 검색한 후 LLM으로 재순위화.
 *
 * 2단계 접근 방식:
 * 1. 벡터 저장소에서 큰 후보 집합(예: 상위 20개)을 검색
 * 2. LLM을 사용해 각 후보의 쿼리 관련성을 점수화
 * 3. 관련성 점수로 재정렬하고 상위 K개 반환
 *
 * 이것은 지연시간과 정밀도의 트레이드오프입니다: LLM은 코사인 유사도보다
 * 느리지만 미묘한 관련성 판단에 훨씬 더 뛰어납니다.
 */
@Component
@Profile("reranker")
public class ReRankingRetrieval implements RetrievalStrategy {

    private static final Logger log = LoggerFactory.getLogger(ReRankingRetrieval.class);
    private static final int MAX_CONTENT_LENGTH = 500;

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    @Value("${retrieval.reranker.candidate-count:20}")
    private int candidateCount;

    public ReRankingRetrieval(VectorStore vectorStore, ChatClient.Builder chatClientBuilder) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public List<Document> retrieve(String query, int topK) {
        // 1단계 — 벡터 저장소에서 광범위한 후보 집합을 검색
        List<Document> candidates = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(candidateCount).build());
        log.debug("[ReRanker] 후보 {}개 검색 완료", candidates.size());

        if (candidates.isEmpty()) {
            return candidates;
        }

        // 2단계 — LLM으로 후보를 재순위화
        List<Double> scores = rerankWithLlm(query, candidates);

        // 3단계 — 문서와 점수를 결합하고 점수 내림차순으로 정렬하여 상위 K개 반환
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
        String prompt = buildScoringPrompt(query, candidates);

        String response = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        return parseScores(response, candidates.size());
    }

    private String buildScoringPrompt(String query, List<Document> candidates) {
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
            // 토큰 절약을 위해 MAX_CONTENT_LENGTH 초과 시 잘라냄
            if (content.length() > MAX_CONTENT_LENGTH) {
                content = content.substring(0, MAX_CONTENT_LENGTH) + "...";
            }
            sb.append("[").append(i + 1).append("] ").append(content).append("\n\n");
        }

        return sb.toString();
    }

    private List<Double> parseScores(String response, int expectedCount) {
        try {
            // LLM이 JSON 배열 앞뒤에 텍스트를 붙일 수 있으므로 배열 구간만 추출
            int start = response.indexOf('[');
            int end = response.lastIndexOf(']');
            if (start == -1 || end == -1) {
                throw new IllegalArgumentException("JSON 배열을 찾을 수 없음");
            }

            List<Double> scores = objectMapper.readValue(
                    response.substring(start, end + 1),
                    new TypeReference<>() {});

            if (scores.size() != expectedCount) {
                log.warn("[ReRanker] 점수 수 불일치: 예상 {}, 실제 {}. 원래 순서 유지",
                        expectedCount, scores.size());
                return Collections.nCopies(expectedCount, 0.5);
            }

            return scores;
        } catch (Exception e) {
            log.warn("[ReRanker] 점수 파싱 실패: {}. 원래 순서 유지", e.getMessage());
            return Collections.nCopies(expectedCount, 0.5);
        }
    }

    private record ScoredDocument(Document document, double score) {}
}

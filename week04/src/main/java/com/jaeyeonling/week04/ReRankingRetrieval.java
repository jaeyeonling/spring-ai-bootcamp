package com.jaeyeonling.week04;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

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

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    @Value("${retrieval.reranker.candidate-count:20}")
    private int candidateCount;

    public ReRankingRetrieval(VectorStore vectorStore, ChatClient.Builder chatClientBuilder) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public List<Document> retrieve(String query, int topK) {
        // TODO: 1단계 — 벡터 저장소에서 광범위한 후보 집합을 검색하세요.
        //
        // topK 대신 candidateCount(기본값 20)를 사용해 더 많은 후보를 가져오세요.
        // SearchRequest.builder().query(query).topK(candidateCount).build()

        List<Document> candidates = List.of(); // TODO: 실제 검색으로 교체

        // TODO: 2단계 — LLM을 사용해 후보를 재순위화하세요.
        //
        // LLM에게 각 문서의 쿼리 관련성을 0.0에서 1.0으로 점수화하도록 요청하는 프롬프트 구성.
        //
        // 예시 프롬프트:
        //   "쿼리: '{query}'가 주어졌을 때
        //    각 문서의 관련성을 0.0에서 1.0으로 점수화하세요.
        //    문서와 같은 순서로 점수의 JSON 배열만 반환하세요.
        //
        //    문서:
        //    [1] {문서 1 내용}
        //    [2] {문서 2 내용}
        //    ..."

        List<Double> scores = rerankWithLlm(query, candidates);

        // TODO: 3단계 — 문서와 점수를 결합하고 점수 내림차순으로 정렬하여
        //       상위 K개 문서를 반환하세요.

        throw new UnsupportedOperationException("재순위화 검색을 구현하세요");
    }

    /**
     * LLM에게 쿼리에 대한 각 문서의 관련성 점수를 요청합니다.
     *
     * @param query      사용자의 질문
     * @param candidates 점수화할 문서들
     * @return 문서당 하나의 관련성 점수 목록 (0.0 ~ 1.0)
     */
    private List<Double> rerankWithLlm(String query, List<Document> candidates) {
        // TODO: 점수화 프롬프트 구성
        String prompt = buildScoringPrompt(query, candidates);

        // TODO: LLM 호출
        // String response = chatClient.prompt().user(prompt).call().content();

        // TODO: 응답에서 점수의 JSON 배열을 파싱하세요
        // 엣지 케이스 처리: 잘못된 JSON, 잘못된 점수 수 등

        throw new UnsupportedOperationException("LLM 기반 재순위화를 구현하세요");
    }

    private String buildScoringPrompt(String query, List<Document> candidates) {
        // TODO: 모든 후보 문서를 나열하고 LLM에게 각각 점수화를 요청하는 프롬프트 구성.
        //
        // 팁:
        // - 토큰을 절약하기 위해 긴 문서를 잘라내세요
        // - 출력 형식을 명시적으로 지정하세요 (JSON 배열)
        // - 일관성을 위해 프롬프트에 예시를 포함하세요

        StringBuilder sb = new StringBuilder();
        sb.append("쿼리: '").append(query).append("'\n\n");
        sb.append("각 문서의 관련성을 0.0에서 1.0으로 점수화하세요.\n");
        sb.append("점수의 JSON 배열만 반환하세요. 예시: [0.9, 0.3, 0.7]\n\n");
        sb.append("문서:\n");

        for (int i = 0; i < candidates.size(); i++) {
            String content = candidates.get(i).getText();
            // TODO: 내용이 합리적인 길이(예: 500자)를 초과하면 잘라내세요
            sb.append("[").append(i + 1).append("] ").append(content).append("\n\n");
        }

        return sb.toString();
    }
}

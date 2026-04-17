package com.jaeyeonling.stage2.m2;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 기준선 검색 전략 — 단순 벡터 유사도 검색.
 *
 * 가장 단순한 접근 방식입니다: 쿼리를 임베딩하고, 벡터 저장소에서
 * 가장 유사한 상위 K개 문서를 찾아 그대로 반환합니다.
 *
 * 예상 Top-1 정확도: ~40%
 */
@Component
@Profile("baseline")
public class BaselineRetrieval implements RetrievalStrategy {

    private final VectorStore vectorStore;

    public BaselineRetrieval(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public List<Document> retrieve(String query, int topK) {
        // TODO: 벡터 저장소를 사용해 단순 유사도 검색을 수행하세요.
        //
        // 단계:
        // 1. 쿼리와 topK로 SearchRequest 구성
        // 2. vectorStore.similaritySearch(...) 호출
        // 3. 결과 반환
        //
        // 힌트: SearchRequest.builder().query(query).topK(topK).build()

        return vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(topK).build());
    }
}

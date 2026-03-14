package com.jaeyeonling.week10;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 기존 RAG 검색 파이프라인을 LLM이 호출할 수 있는 툴로 래핑합니다.
 *
 * 회사 정책, 환불, 배송 등 FAQ 관련 질문에 답하기 위해 벡터 저장소를 검색합니다.
 * 검색된 문서 ID를 포함해 소스 추적이 가능합니다.
 */
@Service
public class FaqSearchTool {

    private static final Logger log = LoggerFactory.getLogger(FaqSearchTool.class);

    private final VectorStore vectorStore;

    public FaqSearchTool(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * 정보를 위해 FAQ와 회사 정책 문서를 검색합니다.
     *
     * @param query 검색 쿼리 (예: "환불 정책", "배송 시간")
     * @return 쿼리와 일치하는 관련 문서 발췌
     */
    @Tool(description = "회사 정책, 환불 규정, 배송 세부사항, 계정 관리 및 기타 일반적인 질문에 대한 " +
                         "정보를 위해 FAQ와 회사 정책 문서를 검색합니다. " +
                         "특정 주문 상태나 매출 수치에 관한 질문이 아닐 때 사용하세요.")
    public String searchFaq(
            @ToolParam(description = "찾을 정보를 설명하는 검색 쿼리") String query) {

        log.info("툴 호출됨: searchFaq(query={})", query);

        var results = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(3)
                        .build());

        if (results.isEmpty()) {
            return "검색에서 관련 FAQ 항목을 찾을 수 없습니다: " + query;
        }

        return results.stream()
                .map(doc -> doc.getText())
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    /**
     * 소스 ID와 함께 FAQ를 검색합니다 (ChatService에서 sources 필드 구성 시 사용).
     *
     * @param query 검색 쿼리
     * @param topK  반환할 최대 문서 수
     * @return 문서 목록
     */
    public List<org.springframework.ai.document.Document> searchWithSources(String query, int topK) {
        return vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .build());
    }
}

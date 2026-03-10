package com.jaeyeonling.week07;

/**
 * 기존 RAG 검색 파이프라인을 LLM이 호출할 수 있는 툴로 래핑합니다.
 *
 * 이를 통해 문서 검색이 API 툴(OrderTools 같은)과 함께 사용 가능해지므로,
 * LLM이 사용자의 질문에 따라 문서를 검색할지 API를 호출할지 결정할 수 있습니다.
 *
 * 핵심 통찰: RAG 검색은 그저 또 하나의 툴입니다. @Tool로 노출함으로써
 * LLM은 같은 대화에서 문서 검색과 API 호출을 혼합할 수 있습니다.
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

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

        // TODO: VectorStore를 사용해 유사도 검색을 수행하세요.
        //  상위 K개의 가장 관련 있는 문서 청크를 하나의 문자열로 반환하세요.
        //
        //  구현 예시:
        //    List<Document> results = vectorStore.similaritySearch(
        //        SearchRequest.builder()
        //            .query(query)
        //            .topK(3)
        //            .build());
        //
        //    if (results.isEmpty()) {
        //        return "검색에서 관련 FAQ 항목을 찾을 수 없습니다: " + query;
        //    }
        //
        //    return results.stream()
        //        .map(Document::getText)
        //        .collect(Collectors.joining("\n\n---\n\n"));

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
}

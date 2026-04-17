package com.jaeyeonling.stage2.m7;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;
/**
 * FAQ 벡터 검색을 LLM 툴로 노출합니다.
 *
 * 이 클래스는 의도적으로 VectorStore만 의존합니다.
 * QueryTransform + ReRanker 로직은 FaqReranker로 분리되어 있습니다.
 *
 * 왜 분리했는가:
 *   ToolCallbackProvider 초기화 시점에 Spring AI는 등록된 @Tool 빈들을 스캔합니다.
 *   이때 toolCallbackProvider → FaqSearchTool → ChatClient.Builder 경로가 생기면,
 *   ChatClient.Builder 초기화가 toolCallbackResolver → toolCallbackProvider를 다시
 *   필요로 하여 순환이 발생합니다.
 *
 *   FaqSearchTool이 VectorStore만 의존하면 이 순환이 구조적으로 불가능합니다.
 *
 * 툴로 호출될 때(LLM → searchFaq):
 *   단순 벡터 유사도 검색만 수행합니다. LLM이 이미 쿼리를 구성했으므로
 *   추가 QueryTransform이 필요하지 않습니다.
 *
 * ChatService에서 소스 추적 용도(searchWithSources):
 *   FaqReranker를 통해 QueryTransform + ReRanker 전체 파이프라인을 사용합니다.
 */
@Service
public class FaqSearchTool {

    private static final Logger log = LoggerFactory.getLogger(FaqSearchTool.class);

    private final VectorStore vectorStore;

    public FaqSearchTool(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * FAQ와 회사 정책 문서를 벡터 유사도 검색으로 검색합니다.
     * LLM 툴 호출 경로 — 단순 벡터 검색만 수행합니다.
     */
    @Tool(description = "회사 정책, 환불 규정, 배송 세부사항, 계정 관리 및 기타 일반적인 질문에 대한 " +
                         "정보를 위해 FAQ와 회사 정책 문서를 검색합니다. " +
                         "특정 주문 상태나 매출 수치에 관한 질문이 아닐 때 사용하세요.")
    public String searchFaq(
            @ToolParam(description = "찾을 정보를 설명하는 검색 쿼리") String query) {

        log.info("툴 호출됨: searchFaq(query={})", query);

        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(3).build());

        if (results.isEmpty()) {
            return "검색에서 관련 FAQ 항목을 찾을 수 없습니다: " + query;
        }

        return results.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n---\n\n"));
    }
}

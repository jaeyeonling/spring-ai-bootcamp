package com.jaeyeonling.week09;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * MCP 호환 클라이언트에 FAQ 기능을 노출하는 MCP 서비스.
 *
 * 이 서비스는 Spring AI의 @Tool 어노테이션을 사용해 MCP 서버에 툴을 등록합니다.
 * 각 @Tool 메서드는 MCP 클라이언트 (예: Claude Desktop, Cursor 등)가 호출할 수 있는 툴이 됩니다.
 *
 * Spring AI 1.1.2는 org.springframework.ai.tool.annotation의 @Tool과 @ToolParam 어노테이션을 사용합니다.
 * MethodToolCallbackProvider.builder().toolObjects(this).build()를 사용하거나
 * ToolCallbackProvider 타입의 Bean으로 반환하여 이 서비스를 툴 공급자로 등록하세요.
 *
 * Resource와 Prompt 프리미티브가 필요하면 @Tool 메서드로 구현하세요.
 * 각 @Tool 메서드는 MCP 클라이언트가 호출할 수 있는 툴로 등록됩니다.
 */
@Service
public class FaqMcpService {

    private final VectorStore vectorStore;

    public FaqMcpService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    // ── 툴 ──────────────────────────────────────────────────────────

    // TODO: searchFaq 툴을 구현하세요.
    //  @Tool로 어노테이션을 달고 AI를 위한 명확한 설명을 제공하세요.
    //  설명에는 다음이 포함되어야 합니다: 툴이 하는 일, 언제 사용하는지,
    //  무엇을 반환하는지.
    //
    //  파라미터:
    //    - query (String): 자연어로 된 사용자의 검색 쿼리
    //    - topK (int): 반환할 최대 결과 수 (기본값 3)
    //
    //  구현:
    //    - vectorStore.similaritySearch(SearchRequest.builder()
    //          .query(query).topK(topK).build()) 사용
    //    - 각 Document를 "content", "metadata", "score" 키가 있는 Map으로 매핑
    //    - List<Map<String, Object>>로 반환
    //
    //  예시:
    //    @Tool(description = "자연어 쿼리로 FAQ 문서를 검색합니다. " +
    //                        "콘텐츠와 메타데이터가 포함된 일치하는 FAQ 항목을 반환합니다. " +
    //                        "사용자가 FAQ에 있을 수 있는 질문을 할 때 사용하세요.")
    //    public List<Map<String, Object>> searchFaq(
    //            @ToolParam(description = "자연어 검색 쿼리") String query,
    //            @ToolParam(description = "반환할 최대 결과 수 (1-10, 기본값 3)") int topK) {
    //        return vectorStore.similaritySearch(
    //                SearchRequest.builder().query(query).topK(topK).build()
    //        ).stream()
    //         .map(doc -> Map.of(
    //                 "content",  (Object) doc.getText(),
    //                 "metadata", doc.getMetadata()
    //         ))
    //         .toList();
    //    }

    // TODO (고급): 사용 가능한 FAQ 카테고리 목록을 반환하는 faqCategories 툴을 구현하세요.
    //
    //  예시:
    //    @Tool(description = "사용 가능한 모든 FAQ 문서 카테고리를 나열합니다.")
    //    public List<Map<String, String>> faqCategories() {
    //        return List.of(
    //            Map.of("id", "shipping",  "title", "배송 및 배달"),
    //            Map.of("id", "returns",   "title", "반품 및 환불"),
    //            Map.of("id", "payment",   "title", "결제"),
    //            Map.of("id", "account",   "title", "계정 및 멤버십"),
    //            Map.of("id", "products",  "title", "상품"),
    //            Map.of("id", "support",   "title", "고객 지원")
    //        );
    //    }
}

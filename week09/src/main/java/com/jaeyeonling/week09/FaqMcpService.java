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
 * 3가지 MCP 프리미티브를 @Tool로 구현:
 * - searchFaq: Tool 역할 (FAQ 검색 액션)
 * - faqCategories: Resource 역할 (FAQ 카테고리 목록 읽기)
 * - customerSupportPrompt: Prompt 역할 (고객지원 프롬프트 템플릿)
 */
@Service
public class FaqMcpService {

    private final VectorStore vectorStore;

    public FaqMcpService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    // ── Tool ──────────────────────────────────────────────────────

    @Tool(description = "자연어 쿼리로 FAQ 문서를 검색합니다. " +
                        "콘텐츠와 메타데이터가 포함된 일치하는 FAQ 항목을 반환합니다. " +
                        "사용자가 환불, 배송, 결제 등 FAQ에 있을 수 있는 질문을 할 때 사용하세요.")
    public List<Map<String, Object>> searchFaq(
            @ToolParam(description = "자연어 검색 쿼리, 예: '환불 정책', '배송 기간'") String query,
            @ToolParam(description = "반환할 최대 결과 수 (1-10, 기본값 3)") int topK) {
        return vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(topK).build()
        ).stream()
         .map(doc -> Map.of(
                 "content",  (Object) doc.getText(),
                 "metadata", doc.getMetadata()
         ))
         .toList();
    }

    // ── Resource ──────────────────────────────────────────────────

    @Tool(description = "사용 가능한 모든 FAQ 문서 카테고리를 나열합니다. " +
                        "사용자가 어떤 종류의 질문을 할 수 있는지 알고 싶을 때 사용하세요.")
    public List<Map<String, String>> faqCategories() {
        return List.of(
            Map.of("id", "shipping",  "title", "배송 및 배달"),
            Map.of("id", "returns",   "title", "반품 및 환불"),
            Map.of("id", "payment",   "title", "결제"),
            Map.of("id", "account",   "title", "계정 및 멤버십"),
            Map.of("id", "products",  "title", "상품"),
            Map.of("id", "support",   "title", "고객 지원")
        );
    }

    // ── Prompt ──────────────────────────────────────────────────

    @Tool(description = "고객 지원 대화를 위한 프롬프트 템플릿을 반환합니다. " +
                        "고객 문의에 응답하는 방법에 대한 가이드라인이 포함되어 있습니다. " +
                        "고객 지원 대화를 시작할 때 이 템플릿을 참고하세요.")
    public Map<String, String> customerSupportPrompt(
            @ToolParam(description = "고객이 제기한 이슈 유형, 예: '환불 요청', '배송 지연'") String issue) {
        String template = """
                당신은 초록 코퍼레이션의 고객 지원 담당자입니다.
                
                고객이 다음 이슈를 제기했습니다: %s
                
                대응 가이드라인:
                1. searchFaq 도구를 사용하여 관련 정책을 먼저 확인하세요.
                2. 구체적인 주문 관련 문의라면 lookupOrder 또는 checkOrderStatus를 사용하세요.
                3. 항상 정중하고 도움이 되는 태도로 응답하세요.
                4. 정책에서 확인할 수 없는 내용은 추측하지 말고 담당 부서 안내를 제공하세요.
                """.formatted(issue);

        return Map.of(
            "name", "customer_support",
            "description", "고객 지원 대화용 프롬프트 템플릿",
            "template", template
        );
    }
}

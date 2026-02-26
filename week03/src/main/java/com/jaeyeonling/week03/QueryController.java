package com.jaeyeonling.week03;

/**
 * 인제스트된 문서를 쿼리하기 위한 REST 컨트롤러.
 *
 * Spring AI의 ChatClient와 VectorStore를 사용해 RAG를 구현합니다:
 * 1. 관련 청크를 위해 Qdrant 검색
 * 2. 컨텍스트로 프롬프트 보강
 * 3. LLM 호출
 * 4. 소스와 토큰 사용량과 함께 답변 반환
 */

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class QueryController {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public QueryController(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
        this.vectorStore = vectorStore;

        // TODO: RAG를 위한 기본 시스템 프롬프트로 ChatClient를 빌드하세요.
        //   Spring AI 1.1.2에서는 chatClientBuilder.defaultSystem(...)을 사용해
        //   시스템 프롬프트를 설정하세요. 실제 컨텍스트 주입은 아래
        //   query 메서드에서 수동으로 처리합니다.
        //   자세한 내용은 hints/HINT_03.md를 참고하세요.

        this.chatClient = chatClientBuilder.build();
    }

    @PostMapping("/query")
    public ResponseEntity<QueryResponse> query(@RequestBody QueryRequest request) {
        // TODO: 요청 검증 (question이 비어있으면 안 됨)

        // TODO: 먼저 vectorStore에서 관련 컨텍스트 문서를 검색하세요.
        //   List<Document> sources = vectorStore.similaritySearch(
        //       SearchRequest.builder().query(request.question()).topK(5).build()
        //   );

        // TODO: 검색된 문서에서 컨텍스트 문자열을 구성하세요.
        //   String context = sources.stream()
        //       .map(Document::getText)
        //       .collect(java.util.stream.Collectors.joining("\n\n"));

        // TODO: 시스템 프롬프트에 컨텍스트를 주입하여 chatClient를 호출하세요.
        //   ChatResponse response = chatClient.prompt()
        //       .system("다음 컨텍스트만을 사용하여 질문에 답하세요:\n\n" + context)
        //       .user(request.question())
        //       .call()
        //       .chatResponse();

        // TODO: ChatResponse에서 답변 텍스트 추출
        //   response.getResult().getOutput().getText()

        // TODO: ChatResponse에서 토큰 사용량 추출
        //   response.getMetadata().getUsage()

        // TODO: 각 소스 Document의 메타데이터 "source" 필드를 소스 레이블로 변환하세요.
        //   sources.stream()
        //       .map(doc -> (String) doc.getMetadata().getOrDefault("source", "unknown"))
        //       .distinct().toList()

        // TODO: 답변, 소스, 토큰 사용량이 포함된 QueryResponse 반환

        throw new UnsupportedOperationException("구현하세요 — mission/MISSION.md 참고");
    }

    public record QueryRequest(String question) {}

    public record QueryResponse(
        String answer,
        List<String> sources,
        TokenUsage tokenUsage
    ) {}

    public record TokenUsage(
        long promptTokens,
        long completionTokens,
        long totalTokens
    ) {}
}

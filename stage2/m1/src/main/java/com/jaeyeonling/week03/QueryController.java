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
        this.chatClient = chatClientBuilder.build();
    }

    @PostMapping("/query")
    public ResponseEntity<QueryResponse> query(@RequestBody QueryRequest request) {
        if (request.question() == null || request.question().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        // 1. VectorStore에서 관련 컨텍스트 문서 검색
        List<Document> sources = vectorStore.similaritySearch(
                SearchRequest.builder().query(request.question()).topK(5).build()
        );

        // 2. 검색된 문서에서 컨텍스트 문자열 구성
        String context = sources.stream()
                .map(Document::getText)
                .collect(java.util.stream.Collectors.joining("\n\n"));

        // 3. 시스템 프롬프트에 컨텍스트를 주입하여 ChatClient 호출
        ChatResponse response = chatClient.prompt()
                .system("다음 컨텍스트만을 사용하여 질문에 답하세요. " +
                        "컨텍스트에 답이 없으면 '해당 내용은 FAQ에서 찾을 수 없습니다'라고 답하세요. " +
                        "질문과 동일한 언어로 답변하세요.\n\n" + context)
                .user(request.question())
                .call()
                .chatResponse();

        // 4. 답변 텍스트 추출
        String answer = response.getResult().getOutput().getText();

        // 5. 토큰 사용량 추출
        var usage = response.getMetadata().getUsage();
        TokenUsage tokenUsage = new TokenUsage(
                usage.getPromptTokens(),
                usage.getCompletionTokens(),
                usage.getTotalTokens()
        );

        // 6. 소스 목록 추출
        List<String> sourceLabels = sources.stream()
                .map(doc -> (String) doc.getMetadata().getOrDefault("source", "unknown"))
                .distinct()
                .toList();

        return ResponseEntity.ok(new QueryResponse(answer, sourceLabels, tokenUsage));
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

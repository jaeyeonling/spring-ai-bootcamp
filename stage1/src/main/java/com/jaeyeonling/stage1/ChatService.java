package com.jaeyeonling.stage1;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * FAQ 문서를 사용해 질문에 답하는 핵심 서비스.
 *
 * RAG (Retrieval-Augmented Generation) 흐름:
 *   1. 앱 시작 시 FAQ 문서를 청크로 분할하고 각 청크를 임베딩 → InMemoryVectorStore에 저장
 *   2. 질문이 들어오면 질문도 임베딩 → 코사인 유사도로 관련 청크 검색
 *   3. 검색된 청크를 컨텍스트로 프롬프트 구성 → LLM 호출
 *   4. 토큰 사용량과 함께 답변 반환
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ChatClient chatClient;
    private final EmbeddingModel embeddingModel;
    private final InMemoryVectorStore vectorStore;
    private final FaqLoader faqLoader;

    @Value("${faq.data-dir}")
    private String faqDataDir;

    // 검색할 청크 수 — 적을수록 토큰 절감, 많을수록 정확도 향상
    private static final int TOP_K = 3;

    // 초기화 완료 후에는 잠금 없이 통과하기 위한 플래그
    private volatile boolean initialized = false;

    public ChatService(ChatClient.Builder chatClientBuilder,
                       EmbeddingModel embeddingModel,
                       InMemoryVectorStore vectorStore,
                       FaqLoader faqLoader) {
        this.chatClient = chatClientBuilder.build();
        this.embeddingModel = embeddingModel;
        this.vectorStore = vectorStore;
        this.faqLoader = faqLoader;
    }

    /**
     * 첫 요청 시 FAQ를 로드하고 벡터 스토어를 초기화합니다.
     * Double-checked locking: 초기화 완료 후에는 volatile 읽기만으로 통과합니다.
     */
    private void ensureInitialized() {
        if (initialized) {
            return;
        }
        synchronized (this) {
            if (initialized) {
                return;
            }
            log.info("=== 벡터 스토어 초기화 시작 ===");

            String document = faqLoader.loadDirectory(faqDataDir);
            log.info("[로드] FAQ 디렉토리: {}, 문서 길이: {} 글자", faqDataDir, document.length());

            List<String> chunks = faqLoader.splitIntoChunks(document);
            log.info("[청킹] {} 개의 청크로 분할", chunks.size());
            for (int i = 0; i < chunks.size(); i++) {
                log.debug("[청킹] 청크[{}]: {}...", i, chunks.get(i).substring(0, Math.min(80, chunks.get(i).length())).replace("\n", " "));
            }

            vectorStore.addAll(chunks, embeddingModel);
            log.info("[임베딩] {} 개의 청크를 벡터로 변환하여 저장 완료", chunks.size());
            log.info("=== 벡터 스토어 초기화 완료 ===");

            initialized = true;
        }
    }

    /**
     * FAQ 문서를 사용해 사용자의 질문에 답합니다.
     *
     * @param question 사용자의 자연어 질문
     * @return 답변과 토큰 사용량이 포함된 ChatController.ChatResponse
     */
    public ChatController.ChatResponse answer(String question) {
        ensureInitialized();

        log.info("=== RAG 파이프라인 시작 ===");
        log.info("[질문] {}", question);

        // 1단계: 질문을 임베딩 → 관련 청크 검색 (RAG의 R)
        float[] queryVector = embeddingModel.embed(question);
        log.info("[R: 임베딩] 질문 → {}차원 벡터 (앞 5개: {})", queryVector.length,
                Arrays.toString(Arrays.copyOf(queryVector, Math.min(5, queryVector.length))));

        List<String> relevantChunks = vectorStore.search(queryVector, TOP_K);
        log.info("[R: 검색] top-{} 청크 검색 완료", relevantChunks.size());
        for (int i = 0; i < relevantChunks.size(); i++) {
            String chunk = relevantChunks.get(i);
            log.info("[R: 검색] #{}: {}...", i + 1, chunk.substring(0, Math.min(100, chunk.length())).replace("\n", " "));
        }

        String context = String.join("\n\n---\n\n", relevantChunks);

        // 2단계: 검색된 컨텍스트로 프롬프트 구성 → LLM 호출 (RAG의 G)
        // ⚠️ 보안 주의: FAQ 데이터가 시스템 프롬프트에 직접 삽입됩니다.
        // 운영 환경에서는 데이터 소스를 신뢰할 수 있는지 검증하거나,
        // 프롬프트 인젝션 방어 (입력 검증, 출력 필터링)를 추가하세요.
        String systemPrompt = """
                당신은 초록 코퍼레이션의 고객지원 도우미입니다.
                아래 FAQ 내용만을 기반으로 질문에 답하세요.
                FAQ에 없는 내용은 "해당 내용은 FAQ에서 찾을 수 없습니다"라고 답하세요.

                FAQ 내용:
                """ + context;

        log.info("[A: 증강] 시스템 프롬프트 구성 완료 (길이: {} 글자, 컨텍스트에 {} 개 청크 포함)", systemPrompt.length(), relevantChunks.size());
        log.debug("[A: 증강] 시스템 프롬프트 전문:\n{}", systemPrompt);

        log.info("[G: 생성] LLM 호출 시작...");
        ChatResponse chatResponse = chatClient.prompt()
                .system(systemPrompt)
                .user(question)
                .call()
                .chatResponse();

        String answer = chatResponse.getResult().getOutput().getText();
        log.info("[G: 생성] LLM 응답 수신 (길이: {} 글자)", answer.length());
        log.info("[G: 생성] 답변: {}", answer);

        // 3단계: 토큰 사용량 추출
        var usage = chatResponse.getMetadata().getUsage();
        log.info("[비용] 토큰 사용량 — 프롬프트: {}, 완성: {}, 합계: {}",
                usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
        log.info("=== RAG 파이프라인 완료 ===");

        return new ChatController.ChatResponse(
                answer,
                new ChatController.TokenUsage(
                        usage.getPromptTokens().intValue(),
                        usage.getCompletionTokens().intValue(),
                        usage.getTotalTokens().intValue()
                )
        );
    }
}

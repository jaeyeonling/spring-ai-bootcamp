package com.jaeyeonling.week02;

import com.jaeyeonling.week01.FaqLoader;
import com.jaeyeonling.week01.InMemoryVectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 1주차에서 개선된 채팅 서비스.
 *
 * 1주차 대비 개선사항:
 * - 교체 가능한 ChunkingStrategy를 받습니다 (하드코딩된 ### 분할 대신)
 * - 퓨샷 및/또는 사고의 사슬 기법을 사용한 개선된 프롬프트
 * - 평가가 쉽도록 답변 문자열만 반환
 *
 * 핵심 통찰: 청킹과 프롬프팅 모두 답변 품질에 영향을 미치며,
 * 각 변경의 효과를 독립적으로 측정할 수 있습니다.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ChatClient chatClient;
    private final EmbeddingModel embeddingModel;
    private final ChunkingStrategy chunkingStrategy;
    private final FaqLoader faqLoader;
    private final InMemoryVectorStore vectorStore;

    @Value("${faq.file-path:../data/faq.md}")
    private String faqFilePath;

    @Value("${evaluation.top-k:3}")
    private int topK;

    public ChatService(
            ChatClient.Builder chatClientBuilder,
            EmbeddingModel embeddingModel,
            ChunkingStrategy chunkingStrategy
    ) {
        this.chatClient = chatClientBuilder.build();
        this.embeddingModel = embeddingModel;
        this.chunkingStrategy = chunkingStrategy;
        this.faqLoader = new FaqLoader();
        this.vectorStore = new InMemoryVectorStore();
    }

    private synchronized void ensureInitialized() {
        if (vectorStore.size() == 0) {
            log.info("[초기화] FAQ 로드: {}, 청킹 전략: {}", faqFilePath, chunkingStrategy.getClass().getSimpleName());

            String document = faqLoader.loadDocument(faqFilePath);
            List<String> chunks = chunkingStrategy.split(document);
            log.info("[초기화] {} 개의 청크 생성", chunks.size());

            vectorStore.addAll(chunks, embeddingModel);
            log.info("[초기화] 벡터 스토어 준비 완료");
        }
    }

    /**
     * FAQ 문서를 사용해 사용자의 질문에 답합니다.
     *
     * @param question 사용자의 자연어 질문
     * @return 답변 텍스트
     */
    public String answerQuestion(String question) {
        ensureInitialized();

        // 1단계: 관련 청크 검색
        float[] queryVector = embeddingModel.embed(question);
        List<String> relevantChunks = vectorStore.search(queryVector, topK);
        String context = String.join("\n\n---\n\n", relevantChunks);

        // 2단계: 프롬프트 구성 + LLM 호출
        String systemPrompt = buildSystemPrompt(context);

        return chatClient.prompt()
                .system(systemPrompt)
                .user(question)
                .call()
                .content();
    }

    /**
     * 컨텍스트와 개선 기법이 포함된 시스템 프롬프트를 구성합니다.
     *
     * @param context 합쳐진 관련 FAQ 청크들
     * @return 시스템 프롬프트 문자열
     */
    String buildSystemPrompt(String context) {
        // 기준선: Week 01과 동일한 프롬프트
        return """
                당신은 초록 코퍼레이션의 고객지원 도우미입니다.
                아래 FAQ 내용만을 기반으로 질문에 답하세요.
                FAQ에 없는 내용은 "해당 내용은 FAQ에서 찾을 수 없습니다"라고 답하세요.
                
                FAQ 내용:
                """ + context;
    }
}

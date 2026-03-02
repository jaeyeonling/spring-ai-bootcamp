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
        // 실험 6: Few-shot + CoT 결합 프롬프트
        // - CoT: 관련 섹션 식별 → 사실 추출 → 답변 작성의 3단계 추론
        // - Few-shot: 구체적 답변 형식 시범 (숫자, 기간, 가격 포함)
        // - 질문 언어로 답변 (cross-language 대응)
        // - 컨텍스트에 답이 없으면 명시적 거부 (환각 방지)
        return """
                당신은 초록 코퍼레이션의 고객지원 도우미입니다.
                아래 FAQ 컨텍스트만을 기반으로 질문에 답하세요.
                
                ## 답변 규칙
                1. 컨텍스트에서 질문과 관련된 부분을 찾으세요.
                2. 관련 부분에서 구체적인 사실(숫자, 가격, 기간, 조건)을 추출하세요.
                3. 추출한 사실을 바탕으로 명확하고 간결하게 답변하세요.
                4. 컨텍스트에 답이 없으면 반드시 "해당 내용은 FAQ에서 찾을 수 없습니다"라고만 답하세요.
                5. 질문과 동일한 언어로 답변하세요. 영어 질문에는 영어로, 한국어 질문에는 한국어로 답합니다.
                
                ## 좋은 답변 예시
                
                Q: How long does standard delivery take?
                A: Standard delivery takes 3-5 business days. Express delivery is available for 3,000 won with next-day arrival.
                
                Q: 반품 기간이 어떻게 되나요?
                A: 배송 후 14일 이내에 미개봉, 미사용 상태이며 원래 포장이 유지된 제품을 반품할 수 있습니다.
                
                Q: What payment methods are accepted?
                A: We accept credit cards (Visa, Mastercard, BC Card), KakaoPay, NaverPay, and Toss. Interest-free installments are available for orders over 50,000 won.
                
                ## FAQ 컨텍스트
                
                """ + context;
    }
}

package com.jaeyeonling.week02;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
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

    private final ChatClient chatClient;
    private final EmbeddingModel embeddingModel;
    private final ChunkingStrategy chunkingStrategy;

    @Value("${faq.file-path:../data/faq.md}")
    private String faqFilePath;

    public ChatService(
            ChatClient.Builder chatClientBuilder,
            EmbeddingModel embeddingModel,
            ChunkingStrategy chunkingStrategy
    ) {
        this.chatClient = chatClientBuilder.build();
        this.embeddingModel = embeddingModel;
        this.chunkingStrategy = chunkingStrategy;
    }

    /**
     * FAQ 문서를 사용해 사용자의 질문에 답합니다.
     *
     * @param question 사용자의 자연어 질문
     * @return 답변 텍스트
     */
    public String answerQuestion(String question) {
        // TODO: 1단계 — FAQ 문서를 로드하고 주입된 chunkingStrategy를 사용해 분할하세요.
        //   faqFilePath에서 파일을 읽으세요.
        //   chunkingStrategy.split(content)를 호출해 청크를 가져오세요.
        //   (최적화: 매 요청마다 하지 않고 시작 시 한 번만 하세요.
        //    지금은 요청마다 해도 정확성에 문제없습니다.)

        // TODO: 2단계 — 청크를 임베딩하고 가장 관련 있는 것들을 찾으세요.
        //   1주차의 InMemoryVectorStore를 재사용하거나 인라인으로 구현하세요.
        //   질문을 임베딩하고, 청크 임베딩과 비교해 상위 3-5개를 가져오세요.

        // TODO: 3단계 — 관련 컨텍스트로 개선된 프롬프트를 구성하세요.
        //   다음 기법 중 하나 또는 둘 다 사용하세요:
        //
        //   퓨샷 프롬프팅:
        //     시스템 프롬프트에 2-3개의 Q&A 예시를 포함하여 LLM이
        //     기대하는 답변 형식을 학습하도록 합니다. 예시는 다음을 보여줘야 합니다:
        //     - 구체적인 숫자/가격/기간
        //     - 간결하지만 완전한 답변
        //     - 도움이 될 때 관련 정보 언급
        //
        //   사고의 사슬 (CoT):
        //     LLM이 단계별로 추론하도록 요청하세요:
        //     "1. 컨텍스트에서 관련 섹션을 파악하세요
        //      2. 구체적인 사실을 추출하세요
        //      3. 명확한 답변을 작성하세요"
        //
        //   자세한 예시는 hints/HINT_02.md를 참고하세요.

        // TODO: 4단계 — LLM을 호출하고 답변을 반환하세요.
        //   chatClient.prompt().system(systemPrompt).user(question).call() 사용
        //   답변 텍스트를 반환하세요.

        throw new UnsupportedOperationException("구현하세요 — 프롬프트 개선부터 시작하세요");
    }

    /**
     * 컨텍스트와 개선 기법이 포함된 시스템 프롬프트를 구성합니다.
     *
     * @param context 합쳐진 관련 FAQ 청크들
     * @return 시스템 프롬프트 문자열
     */
    String buildSystemPrompt(String context) {
        // TODO: 개선된 시스템 프롬프트를 구성하세요.
        //   최소한 컨텍스트와 기본 지침을 포함하세요.
        //   그 후 다음 중 하나 또는 둘 다 추가하세요:
        //
        //   퓨샷 예시 (기대하는 답변 품질과 형식을 보여주는 2-3개의 Q&A 예시 추가):
        //
        //     "좋은 답변 예시:
        //      Q: 표준 배송은 얼마나 걸리나요?
        //      A: 표준 배송은 영업일 기준 3-5일입니다. 더 빠른 배송을 원하시면
        //         익스프레스 배송이 3,000원에 익일 배송을 제공합니다.
        //      ..."
        //
        //   사고의 사슬 지침:
        //
        //     "단계별로 생각하세요:
        //      1. 컨텍스트에서 관련 섹션 찾기
        //      2. 구체적인 사실 추출 (숫자, 가격, 기간)
        //      3. 명확하고 간결한 답변 작성"

        throw new UnsupportedOperationException("구현하세요");
    }
}

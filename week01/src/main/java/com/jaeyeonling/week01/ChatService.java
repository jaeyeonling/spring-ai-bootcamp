package com.jaeyeonling.week01;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

/**
 * FAQ 문서를 사용해 질문에 답하는 핵심 서비스.
 *
 * 여러분이 할 일:
 * 1. FAQ 문서를 로드하고 청크로 분할합니다
 * 2. 각 청크를 벡터로 임베딩합니다
 * 3. 질문이 들어오면 가장 관련 있는 청크를 찾습니다
 * 4. 해당 청크를 컨텍스트로 프롬프트를 구성합니다
 * 5. LLM을 호출하고 토큰 사용량과 함께 답변을 반환합니다
 *
 * 원하는 방식으로 접근할 수 있습니다. 가장 단순한 방법
 * (전체 FAQ를 프롬프트에 넣기)은 지금은 동작하지만
 * 곧 한계에 부딪힐 겁니다. 그때 hints/ 폴더를 확인하세요.
 */
@Service
public class ChatService {

    private final ChatClient chatClient;
    private final EmbeddingModel embeddingModel;

    public ChatService(ChatClient.Builder chatClientBuilder, EmbeddingModel embeddingModel) {
        this.chatClient = chatClientBuilder.build();
        this.embeddingModel = embeddingModel;
    }

    /**
     * FAQ 문서를 사용해 사용자의 질문에 답합니다.
     *
     * @param question 사용자의 자연어 질문
     * @return 답변과 토큰 사용량이 포함된 ChatController.ChatResponse
     */
    public ChatController.ChatResponse answer(String question) {
        // TODO: 1단계 — FAQ 문서 로드 (힌트: FaqLoader 참고)
        //   FAQ 파일을 어떻게 찾나요? 프로젝트 루트 기준 ../data/faq.md에 있습니다.
        //   application.yml로 경로를 설정 가능하게 만들 수도 있습니다.

        // TODO: 2단계 — FAQ의 관련 섹션 찾기
        //   옵션 A (단순): 전체 문서를 컨텍스트로 사용.
        //   옵션 B (개선): 청크로 분할, 임베딩, 유사도로 검색.
        //   A로 시작하세요. 토큰 비용을 보면 B를 원하게 됩니다.

        // TODO: 3단계 — 컨텍스트로 프롬프트 구성 후 LLM 호출
        //   chatClient.prompt().system(...).user(...).call() 사용
        //   시스템 프롬프트는 LLM이 제공된 컨텍스트에만 기반해 답하도록 지시해야 합니다.

        // TODO: 4단계 — 응답에서 토큰 사용량 추출
        //   전체 응답(메타데이터 포함)을 얻으려면 .call().content() 대신
        //   .call().chatResponse()를 사용하세요.

        throw new UnsupportedOperationException("구현하세요 — 작동하는 가장 단순한 방법부터 시작하세요");
    }
}

package com.jaeyeonling.week07;

/**
 * RAG 기반 질문과 툴 호출 질문을 모두 처리하는 REST 컨트롤러.
 *
 * LLM이 툴 간에 자동으로 라우팅합니다 — 수동 if/else 로직 없음.
 * 등록된 모든 @Tool 빈이 ChatClient에 전달되고, 모델이
 * 사용자의 질문에 따라 어떤 툴을 호출할지 결정합니다.
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatClient chatClient;
    private final OrderTools orderTools;
    private final FaqSearchTool faqSearchTool;
    private final ReflectionService reflectionService;

    public ChatController(ChatClient.Builder builder,
                          OrderTools orderTools,
                          FaqSearchTool faqSearchTool,
                          ReflectionService reflectionService) {
        this.chatClient = builder.build();
        this.orderTools = orderTools;
        this.faqSearchTool = faqSearchTool;
        this.reflectionService = reflectionService;
    }

    /**
     * 단순 채팅 엔드포인트 — LLM이 자동으로 올바른 툴을 선택합니다.
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        // TODO: 요청 검증 (question이 비어있으면 안 됨)

        // TODO: 모든 툴이 등록된 상태로 chatClient를 사용하세요.
        //  LLM이 질문에 따라 자동으로 어떤 툴을 호출할지 결정합니다.
        //  예시:
        //    String answer = chatClient.prompt()
        //        .system("당신은 도움이 되는 고객 지원 담당자입니다. " +
        //                "주문, 매출, 회사 정책에 관한 질문에 답하기 위해 사용 가능한 툴을 사용하세요.")
        //        .user(request.question())
        //        .tools(orderTools, faqSearchTool)
        //        .call()
        //        .content();

        // TODO: ChatResponse로 래핑하여 답변 반환

        throw new UnsupportedOperationException("구현하세요 — mission/MISSION.md 참고");
    }

    /**
     * Reflection이 포함된 채팅 엔드포인트 — 반환 전에 답변을 검증합니다.
     */
    @PostMapping("/chat/verified")
    public ResponseEntity<ChatResponse> chatWithReflection(@RequestBody ChatRequest request) {
        // TODO: 요청 검증

        // TODO: 직접 chatClient 호출 대신 reflectionService.chatWithReflection()을 사용하세요.
        //  이것은 잘못된 툴 호출이나 나쁜 답변을 잡는 검증 단계를 추가합니다.
        //  예시:
        //    String answer = reflectionService.chatWithReflection(
        //        request.question(), orderTools, faqSearchTool);

        // TODO: ChatResponse로 래핑하여 답변 반환

        throw new UnsupportedOperationException("구현하세요 — mission/MISSION.md 참고");
    }

    public record ChatRequest(String question) {}

    public record ChatResponse(
        String answer,
        boolean verified
    ) {}
}

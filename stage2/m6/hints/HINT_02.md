# 힌트 02: Orchestrator-Worker 패턴 구현

## Agent 인터페이스

```java
public interface Agent {
    String getName();
    String getDescription();  // Orchestrator가 어떤 에이전트를 선택할지 판단 근거
    AgentResponse process(AgentRequest request);
}

public record AgentRequest(
    String question,
    Map<String, Object> context  // 이전 에이전트 결과 전달
) {}

public record AgentResponse(
    String agentName,
    String answer,
    List<Document> sources,
    double confidence  // 0.0-1.0
) {}
```

## Worker 에이전트 구현

```java
@Service
public class FaqAgent implements Agent {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    @Override
    public String getName() { return "FaqAgent"; }

    @Override
    public String getDescription() {
        return "공식 FAQ 문서에서 답변을 검색합니다. "
             + "주문, 배송, 반품, 결제 등 표준 정책 질문에 적합합니다.";
    }

    @Override
    public AgentResponse process(AgentRequest request) {
        // Layer 1 FAQ만 검색
        List<Document> docs = vectorStore.similaritySearch(
            SearchRequest.query(request.question())
                .withTopK(3)
                .withFilterExpression("layer == 'faq'")
        );

        String answer = chatClient.prompt()
            .system("FAQ 문서 기반으로만 답하세요. 없으면 '관련 FAQ 없음'이라고 하세요.")
            .user(buildPrompt(request.question(), docs))
            .call()
            .content();

        double confidence = docs.isEmpty() ? 0.1 : 0.8;
        return new AgentResponse(getName(), answer, docs, confidence);
    }
}
```

PolicyAgent, ChatHistoryAgent도 동일한 패턴으로 구현합니다 (레이어 필터만 변경).

## Orchestrator 구현

```java
@Service
public class OrchestratorAgent {

    private final List<Agent> agents;  // Spring이 모든 Agent 구현체 자동 주입
    private final ChatClient chatClient;

    public String answer(String question) {
        // 1. 관련 에이전트 선택
        List<Agent> selectedAgents = selectAgents(question);

        // 2. 병렬로 Worker 실행 (CompletableFuture)
        List<AgentResponse> responses = selectedAgents.stream()
            .map(agent -> CompletableFuture.supplyAsync(
                () -> agent.process(new AgentRequest(question, Map.of()))
            ))
            .map(CompletableFuture::join)
            .toList();

        // 3. 결과 종합
        return synthesize(question, responses);
    }

    private List<Agent> selectAgents(String question) {
        // LLM이 어떤 에이전트가 필요한지 판단
        String agentList = agents.stream()
            .map(a -> "- " + a.getName() + ": " + a.getDescription())
            .collect(Collectors.joining("\n"));

        String selection = chatClient.prompt()
            .user("""
                질문: %s

                사용 가능한 에이전트:
                %s

                이 질문에 필요한 에이전트 이름을 JSON 배열로 반환하세요: ["AgentName", ...]
                """.formatted(question, agentList))
            .call()
            .content();

        List<String> selectedNames = parseNames(selection);
        return agents.stream()
            .filter(a -> selectedNames.contains(a.getName()))
            .toList();
    }

    private String synthesize(String question, List<AgentResponse> responses) {
        String agentAnswers = responses.stream()
            .map(r -> "[%s (신뢰도 %.1f)]\n%s".formatted(r.agentName(), r.confidence(), r.answer()))
            .collect(Collectors.joining("\n\n"));

        return chatClient.prompt()
            .user("""
                고객 질문: %s

                각 전문 에이전트의 답변:
                %s

                위 답변들을 종합하여 고객에게 최종 답변을 제공하세요.
                모순이 있다면 최신 정책(PolicyAgent)을 우선하세요.
                """.formatted(question, agentAnswers))
            .call()
            .content();
    }
}
```

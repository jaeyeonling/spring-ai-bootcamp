# 힌트 03: Orchestrator-Worker 패턴

Chain은 순차적 실행을 강제합니다. 하지만 코드 리뷰, 테스트 작성, 문서 작성은 종종
**독립적**입니다 — 병렬로 실행할 수 있습니다. Orchestrator-Worker 패턴은 작업을 분해하고,
워커에게 전달하고, 결과를 합성하는 조율자를 추가합니다.

## Orchestrator-Worker 사용 시기

다음과 같을 때 사용:
- 서브태스크가 독립적이고 병렬로 실행 가능한 경우
- 복잡한 작업을 분해하는 중앙 조율자가 필요한 경우
- 최종 결과가 여러 에이전트의 출력을 합성해야 하는 경우
- 실행 순서에 유연성을 원하는 경우 (DAG, 선형 chain이 아닌)

## 동작 방식

```
입력 -> 오케스트레이터 -> [서브태스크로 분해]
                       -> 워커 A (병렬)
                       -> 워커 B (병렬)
                       -> 워커 C (병렬)
                       -> [결과 합성]
                       -> 최종 출력
```

오케스트레이터는 세 가지 역할이 있습니다:
1. **분해** — 입력을 서브태스크로 나눔
2. **전달** — 서브태스크를 적절한 워커로 보냄
3. **합성** — 워커 출력을 일관된 결과로 결합

## Spring AI 구현

### 오케스트레이터

```java
@Service
public class OrchestratorService {

    private final ChatClient chatClient;
    private final List<Agent> agents;

    public OrchestratorService(ChatClient.Builder builder, List<Agent> agents) {
        this.chatClient = builder.build();
        this.agents = agents;
    }

    public String orchestrate(String prDiff) {
        // 1단계: 분해 — LLM에게 계획 생성 요청
        String plan = chatClient.prompt()
                .system("""
                        당신은 프로젝트 조율자입니다. 이 PR diff를 분석하고
                        리뷰 계획을 세우세요. 각 리뷰 측면에 대해 구체적인
                        집중 영역을 제공하세요:
                        - code_review: 코드에서 무엇을 찾을지
                        - test_suggestions: 어떤 테스트 시나리오를 커버할지
                        - doc_updates: 어떤 문서가 변경될 수 있는지
                        
                        키가 code_review, test_suggestions, doc_updates인 JSON으로 형식화하세요.
                        각 값은 특정 지침 문자열이어야 합니다.
                        """)
                .user(prDiff)
                .call()
                .content();

        // 2단계: 전달 — 에이전트 병렬 실행
        Map<String, CompletableFuture<String>> futures = new HashMap<>();

        for (Agent agent : agents) {
            String agentName = agent.getClass().getSimpleName();
            futures.put(agentName, CompletableFuture.supplyAsync(() ->
                    agent.execute(prDiff, plan)
            ));
        }

        // 모든 에이전트 완료 대기
        Map<String, String> results = new HashMap<>();
        futures.forEach((name, future) -> {
            try {
                results.put(name, future.get(60, TimeUnit.SECONDS));
            } catch (Exception e) {
                results.put(name, "에이전트 실패: " + e.getMessage());
            }
        });

        // 3단계: 합성 — 결과를 일관된 보고서로 결합
        String synthesized = chatClient.prompt()
                .system("""
                        당신은 기술 편집자입니다. 이 리뷰 결과들을
                        하나의 일관된 PR 리뷰 보고서로 결합하세요. 중복을 제거하세요.
                        이슈를 심각도 순으로 우선순위화하세요. 명확한 섹션으로 형식화하세요.
                        """)
                .user(results.entrySet().stream()
                        .map(e -> "## " + e.getKey() + "\n" + e.getValue())
                        .collect(Collectors.joining("\n\n")))
                .call()
                .content();

        return synthesized;
    }
}
```

### CompletableFuture를 사용한 병렬 실행

chain 대비 핵심 장점: **워커가 동시에 실행됩니다**.

```
Chain:      |--CodeReview--|--TestWriter--|--DocWriter--|  = 9초
병렬:       |--CodeReview--|
            |--TestWriter--|                              = 3초
            |--DocWriter---|
```

각 에이전트가 ~3초 걸리면, chain은 ~9초, 오케스트레이터는 ~3초가 걸립니다.

### DAG 실행 (고급)

에이전트들이 부분적 의존성을 가질 때도 있습니다. 코드 리뷰는 테스트 작성 전에 실행되어야 하지만,
문서 작성은 독립적입니다:

```java
public String orchestrateWithDag(String prDiff) {
    // 1단계: 독립적 작업 (병렬)
    CompletableFuture<String> reviewFuture = CompletableFuture.supplyAsync(() ->
            codeReviewAgent.execute(prDiff, ""));
    CompletableFuture<String> docsFuture = CompletableFuture.supplyAsync(() ->
            docWriterAgent.execute(prDiff, ""));

    // 2단계: 의존적 작업 (리뷰 대기)
    String review = reviewFuture.join();
    CompletableFuture<String> testsFuture = CompletableFuture.supplyAsync(() ->
            testWriterAgent.execute(prDiff, review));

    // 모든 결과 수집
    String docs = docsFuture.join();
    String tests = testsFuture.join();

    return synthesize(review, tests, docs);
}
```

이렇게 하면 실제 의존성을 준수하면서 가능한 곳에서 병렬성의 이점을 얻을 수 있습니다.

## 트레이드오프

| 장점 | 단점 |
|-----|-----|
| 병렬 실행 — 더 빠른 총 지연시간 | 더 복잡한 구현 |
| 중앙 조율 — 추론 용이 | 오케스트레이터가 단일 장애점 |
| 합성기가 일관된 최종 출력 생성 | 분해와 합성을 위한 추가 LLM 호출 |
| 부분적 의존성을 위한 DAG 지원 | 에이전트 실패를 우아하게 처리해야 함 |

## 패턴 결합

실제로는 패턴을 결합합니다:

1. 어떤 에이전트가 필요한지 결정하기 위해 **Route**
2. 선택된 에이전트를 DAG 기반 병렬 실행으로 **Orchestrate**
3. 내부 멀티 스텝 로직을 가진 에이전트 내에서 **Chain**

이것이 운영 멀티 에이전트 시스템이 작동하는 방식입니다. 단순하게 시작(chain),
낭비가 보이면 라우팅 추가, 지연시간이 중요해지면 오케스트레이션 추가.

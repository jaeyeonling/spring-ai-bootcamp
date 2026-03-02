---
name: multi-agent
description: 멀티 에이전트 워크플로우 패턴 (Chain, Routing, Orchestrator-Worker) 가이드
---

# Multi-Agent Workflow Guide

## 왜 멀티 에이전트인가?

단일 프롬프트로 여러 작업을 시키면 결과가 얕습니다.

```
단일 에이전트:
  "코드 리뷰 + 테스트 작성 + 문서 업데이트를 해줘"
  → 모든 결과가 피상적

멀티 에이전트:
  CodeReviewAgent → 깊은 코드 분석
  TestWriterAgent → 구체적 테스트 케이스
  DocWriterAgent  → 정확한 문서 업데이트
  → 각 분야에서 전문적인 결과
```

## 에이전트 인터페이스

```java
public interface Agent {
    /**
     * 에이전트가 작업을 수행합니다.
     *
     * @param input 입력 데이터
     * @return 작업 결과
     */
    String execute(String input);

    /**
     * 이 에이전트가 해당 입력을 처리할 수 있는지 판단합니다.
     */
    boolean canHandle(String input);
}
```

## 패턴 1: Chain (순차 실행)

에이전트가 **순서대로** 실행됩니다. 이전 에이전트의 출력이 다음 에이전트의 입력이 됩니다.

```
Input → [Agent A] → output A → [Agent B] → output B → [Agent C] → Final Output
```

### 구현

```java
public class ChainWorkflow {
    private final List<Agent> agents;

    public String execute(String input) {
        String result = input;
        for (Agent agent : agents) {
            result = agent.execute(result);
        }
        return result;
    }
}
```

### 사용 예: PR 리뷰 체인

```
PR Diff → [CodeReviewAgent] → 리뷰 결과 → [TestWriterAgent] → 테스트 제안
  (발견된 이슈를 대상으로 테스트 작성)
```

## 패턴 2: Routing (조건부 라우팅)

분류기가 입력을 분석하여 **적절한 에이전트**로 라우팅합니다.

```
Input → [Router/Classifier] → Agent A (코드 변경인 경우)
                             → Agent B (문서 변경인 경우)
                             → Agent C (설정 변경인 경우)
```

### 구현

```java
public class RoutingWorkflow {
    private final ChatClient classifier;
    private final Map<String, Agent> agents;

    public String execute(String input) {
        // LLM이 입력 유형을 분류
        String category = classifier.prompt()
            .system("입력을 분류하세요: CODE, DOCS, CONFIG 중 하나")
            .user(input)
            .call()
            .content();

        Agent agent = agents.get(category);
        return agent.execute(input);
    }
}
```

## 패턴 3: Orchestrator-Worker (병렬 실행)

중앙 오케스트레이터가 작업을 분해하고, **워커 에이전트에게 병렬로** 전달한 후 결과를 합성합니다.

```
Input → [Orchestrator] ─┬─→ [Worker A] ──→ result A ─┐
                        ├─→ [Worker B] ──→ result B ──┼→ [Synthesizer] → Output
                        └─→ [Worker C] ──→ result C ─┘
```

### 구현

```java
@Service
public class OrchestratorService {
    private final List<Agent> workers;
    private final ExecutorService executor = Executors.newFixedThreadPool(3);

    public String orchestrate(String input) {
        // 1. 작업 분배 (병렬)
        List<CompletableFuture<String>> futures = workers.stream()
            .map(worker -> CompletableFuture.supplyAsync(
                () -> worker.execute(input), executor))
            .toList();

        // 2. 모든 결과 수집
        List<String> results = futures.stream()
            .map(f -> {
                try {
                    return f.get(30, TimeUnit.SECONDS);
                } catch (Exception e) {
                    return "에이전트 실행 실패: " + e.getMessage();
                }
            })
            .toList();

        // 3. 결과 합성
        return synthesize(results);
    }

    private String synthesize(List<String> results) {
        return chatClient.prompt()
            .system("다음 에이전트 결과들을 하나의 통합 보고서로 합성하세요.")
            .user(String.join("\n---\n", results))
            .call()
            .content();
    }
}
```

## 에이전트 구현 예시

### CodeReviewAgent

```java
@Component
public class CodeReviewAgent implements Agent {
    private final ChatClient chatClient;

    @Override
    public String execute(String prDiff) {
        return chatClient.prompt()
            .system("""
                당신은 시니어 코드 리뷰어입니다.
                PR diff를 분석하여 다음을 검토하세요:
                - 버그 가능성
                - 코드 스타일
                - 보안 이슈
                - 성능 문제
                각 이슈에 파일명, 라인, 설명, 심각도를 포함하세요.
                """)
            .user(prDiff)
            .call()
            .content();
    }

    @Override
    public boolean canHandle(String input) {
        return input.contains("diff") || input.contains("@@");
    }
}
```

## 패턴 비교

| 패턴 | 장점 | 단점 | 적합한 경우 |
|------|------|------|------------|
| Chain | 단순, 예측 가능 | 순차적 (느림) | 단계간 의존성 있을 때 |
| Routing | 효율적, 필요한 것만 실행 | 분류 정확도에 의존 | 입력 유형이 다양할 때 |
| Orchestrator | 병렬 (빠름), 독립적 | 복잡, 결과 합성 필요 | 독립적 작업이 여러 개일 때 |

## 단일 vs 멀티 에이전트 비교

| 차원 | 단일 프롬프트 | 멀티 에이전트 |
|------|-------------|-------------|
| 깊이 | 얕음 | 각 영역에서 깊음 |
| 토큰 | 1회 호출 | N회 호출 (더 많음) |
| 지연 | 빠름 | 느림 (병렬화 가능) |
| 유지보수 | 프롬프트 하나 | 에이전트별 관리 |
| 확장성 | 프롬프트 비대화 | 에이전트 추가 용이 |

## 관련 스킬

- `function-calling`: @Tool, Reflection 패턴
- `spring-ai-api`: ChatClient API 상세

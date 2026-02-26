# 힌트 02: Routing 워크플로우 패턴

모든 PR이 모든 에이전트를 필요로 하지는 않습니다. 문서만 변경된 PR에는 코드 리뷰나
테스트 제안이 필요 없습니다. 테스트만 변경된 PR에는 문서 업데이트가 필요 없습니다.
Routing을 사용하면 **관련 없는 에이전트를 건너뜁니다**.

## Routing 사용 시기

다음과 같을 때 Routing 사용:
- 입력 타입이 다양하고 다른 처리가 필요한 경우
- 모든 에이전트를 실행하면 단순한 입력에 토큰과 시간을 낭비하는 경우
- 카테고리별로 처리를 전문화하고 싶은 경우

## 동작 방식

```
입력 -> 분류기 -> 경로 A -> 에이전트 A -> 출력
                -> 경로 B -> 에이전트 B -> 출력
                -> 경로 C -> 에이전트 C -> 출력
```

분류기 에이전트(또는 단순 휴리스틱)가 입력을 검토하고 어떤 에이전트를 호출할지 결정합니다.

## Spring AI 구현

### 1단계: 분류기 구성

LLM 자체를 사용해 PR 분류:

```java
public enum PrType {
    CODE_CHANGE,
    TEST_CHANGE,
    DOC_CHANGE,
    CONFIG_CHANGE,
    MIXED
}

@Service
public class PrClassifier {

    private final ChatClient chatClient;

    public PrClassifier(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public PrType classify(String prDiff) {
        String result = chatClient.prompt()
                .system("""
                        다음 PR diff를 정확히 하나의 카테고리로 분류하세요:
                        - CODE_CHANGE: 애플리케이션 소스 코드 변경
                        - TEST_CHANGE: 테스트 파일만 변경
                        - DOC_CHANGE: 문서 파일만 변경
                        - CONFIG_CHANGE: 설정 파일만 변경
                        - MIXED: 여러 카테고리에 걸친 변경
                        
                        카테고리 이름만 응답하고, 다른 것은 포함하지 마세요.
                        """)
                .user(prDiff)
                .call()
                .content();

        return PrType.valueOf(result.trim());
    }
}
```

### 2단계: 분류 기반 라우팅

```java
@Service
public class RoutingWorkflow {

    private final PrClassifier classifier;
    private final Map<String, Agent> agents;

    public RoutingWorkflow(PrClassifier classifier, List<Agent> agentList) {
        this.classifier = classifier;
        this.agents = agentList.stream()
                .collect(Collectors.toMap(
                        a -> a.getClass().getSimpleName(),
                        Function.identity()
                ));
    }

    public String route(String prDiff) {
        PrType type = classifier.classify(prDiff);

        return switch (type) {
            case CODE_CHANGE -> {
                String review = agents.get("CodeReviewAgent").execute(prDiff, "");
                String tests = agents.get("TestWriterAgent").execute(prDiff, review);
                yield combineResults(review, tests, "해당 없음 — 문서 변경 없음");
            }
            case DOC_CHANGE -> {
                String docs = agents.get("DocWriterAgent").execute(prDiff, "");
                yield combineResults("해당 없음 — 문서만", "해당 없음 — 문서만", docs);
            }
            case TEST_CHANGE -> {
                String review = agents.get("CodeReviewAgent").execute(prDiff, "");
                yield combineResults(review, "해당 없음 — PR이 테스트 변경", "해당 없음");
            }
            case MIXED -> {
                // 혼합 PR에는 모든 에이전트 실행
                String review = agents.get("CodeReviewAgent").execute(prDiff, "");
                String tests = agents.get("TestWriterAgent").execute(prDiff, review);
                String docs = agents.get("DocWriterAgent").execute(prDiff, review);
                yield combineResults(review, tests, docs);
            }
            default -> "알 수 없는 PR 타입: " + type;
        };
    }

    private String combineResults(String review, String tests, String docs) {
        return """
                # PR 리뷰 보고서
                
                ## 코드 리뷰
                %s
                
                ## 제안된 테스트
                %s
                
                ## 문서 업데이트
                %s
                """.formatted(review, tests, docs);
    }
}
```

## 더 저렴한 대안: 휴리스틱 라우팅

항상 LLM으로 분류할 필요가 없습니다. 파일 확장자도 작동합니다:

```java
public PrType classifyByFileExtensions(String prDiff) {
    boolean hasJava = prDiff.contains(".java") &&
            !prDiff.contains("src/test/");
    boolean hasTests = prDiff.contains("src/test/");
    boolean hasDocs = prDiff.contains(".md") || prDiff.contains(".adoc");

    if (hasJava && !hasTests && !hasDocs) return PrType.CODE_CHANGE;
    if (!hasJava && hasTests && !hasDocs) return PrType.TEST_CHANGE;
    if (!hasJava && !hasTests && hasDocs) return PrType.DOC_CHANGE;
    return PrType.MIXED;
}
```

이것은 토큰이 0이고 마이크로초 만에 실행됩니다. 휴리스틱이 충분히 신뢰할 수 없을 때만
LLM 분류를 사용하세요.

## 트레이드오프

| 장점 | 단점 |
|-----|-----|
| 불필요한 에이전트 건너뜀 — 토큰과 시간 절약 | 분류가 틀릴 수 있음 |
| 각 경로가 입력 타입에 최적화됨 | 더 복잡한 제어 흐름 |
| 휴리스틱 라우팅은 무료 | LLM 기반 라우팅은 왕복이 추가됨 |

## 다음 할 일

Routing은 **어떤** 에이전트를 실행할지 결정합니다. Orchestrator-Worker 패턴은
**어떻게** 에이전트를 조율할지 결정합니다 — 병렬 실행 포함. 힌트 03을 참고하세요.

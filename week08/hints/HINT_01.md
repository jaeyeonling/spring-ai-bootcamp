# 힌트 01: Chain 워크플로우 패턴

가장 단순한 멀티 에이전트 패턴입니다. 에이전트가 **순차적으로** 실행됩니다 — 각 에이전트의 출력이
다음 에이전트의 입력이 됩니다 (또는 컨텍스트의 일부가 됩니다).

## Chain 사용 시기

작업이 **자연스러운 의존성 순서**를 가질 때 Chain 사용:

1. 코드 리뷰가 이슈 발견 -> 테스트 작성자가 해당 이슈를 대상으로 -> 문서 작성자가 수정 사항 반영
2. 요약자가 핵심 사항 추출 -> 번역자가 다른 언어로 변환
3. 플래너가 단계 생성 -> 실행자가 각 단계 실행 -> 검증자가 결과 확인

핵심 제약: **각 단계는 이전 단계의 출력에 의존합니다**.

## 동작 방식

```
입력 -> 에이전트 A -> 출력 A -> 에이전트 B -> 출력 B -> 에이전트 C -> 최종 출력
```

에이전트 B는 원래 입력만 받는 것이 아닙니다 — 에이전트 A의 출력을 추가 컨텍스트로 받습니다.
이를 통해 각 에이전트가 이전 에이전트의 작업 위에 구축할 수 있습니다.

## Spring AI 구현

각 에이전트는 역할별 시스템 프롬프트로 자체 `ChatClient` 호출을 사용합니다:

```java
@Service
public class ChainWorkflow {

    private final ChatClient chatClient;

    public ChainWorkflow(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public String runChain(String prDiff) {
        // 1단계: 코드 리뷰
        String codeReview = chatClient.prompt()
                .system("당신은 시니어 코드 리뷰어입니다. 다음 PR diff를 " +
                        "버그, 보안 이슈, 성능 문제, 스타일 위반에 대해 분석하세요. " +
                        "구체적으로 — 줄 번호를 참조하고 각 이슈가 왜 중요한지 설명하세요.")
                .user(prDiff)
                .call()
                .content();

        // 2단계: 테스트 작성자 — diff와 리뷰 모두 받음
        String testSuggestions = chatClient.prompt()
                .system("당신은 테스트 엔지니어입니다. PR diff와 코드 리뷰가 주어지면, " +
                        "발견된 이슈를 커버하는 구체적인 테스트 케이스를 제안하세요. " +
                        "각 테스트에 대해 제공하세요: 테스트명, 설정, 동작, 단언.")
                .user("## PR Diff\n" + prDiff + "\n\n## 코드 리뷰\n" + codeReview)
                .call()
                .content();

        // 3단계: 문서 작성자 — diff, 리뷰, 테스트 모두 받음
        String docUpdates = chatClient.prompt()
                .system("당신은 기술 작가입니다. PR diff, 코드 리뷰, " +
                        "제안된 테스트가 주어지면, 어떤 문서를 업데이트해야 하는지 파악하세요. " +
                        "어떤 문서, 어떤 섹션, 무엇을 변경할지 구체적으로 기술하세요.")
                .user("## PR Diff\n" + prDiff +
                      "\n\n## 코드 리뷰\n" + codeReview +
                      "\n\n## 제안된 테스트\n" + testSuggestions)
                .call()
                .content();

        return combineResults(codeReview, testSuggestions, docUpdates);
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

## 핵심 통찰: 컨텍스트 누적

각 에이전트가 이전 에이전트보다 **더 많은 컨텍스트**를 받는 것에 주목하세요:

| 에이전트 | 입력 |
|-------|-------|
| CodeReviewAgent | PR diff |
| TestWriterAgent | PR diff + 코드 리뷰 |
| DocWriterAgent | PR diff + 코드 리뷰 + 테스트 제안 |

이것이 chain의 강점입니다: **컨텍스트가 누적됩니다**. 나중 에이전트들이 이전 에이전트들이
발견한 것을 보기 때문에 더 나은 결정을 내립니다.

## 트레이드오프

| 장점 | 단점 |
|-----|-----|
| 구현이 단순함 | 순차적 — 총 지연시간 = 모든 에이전트 지연시간의 합 |
| 각 에이전트가 이전 컨텍스트의 혜택을 받음 | 오류가 전파됨 — 나쁜 리뷰가 나쁜 테스트로 이어짐 |
| 단계별 디버깅 용이 | 독립적인 작업을 병렬화할 수 없음 |

## 다음 할 일

Chain은 순차적입니다 — 모든 에이전트가 이전 에이전트를 기다립니다.
하지만 코드 리뷰, 테스트 작성, 문서 작성은 때로 **병렬**로 실행할 수 있습니다.

그것이 Orchestrator-Worker 패턴이 하는 일입니다. 힌트 03을 참고하세요.

하지만 먼저 — 일부 PR에는 세 에이전트가 모두 필요하지 않다면 어떨까요? 힌트 02에서 Routing을 참고하세요.

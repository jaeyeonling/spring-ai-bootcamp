# Week 08 — 멀티 에이전트 워크플로우: Chain, Orchestrator-Worker

> **미션**: 단일 프롬프트로는 코드 리뷰 + 테스트 제안 + 문서 업데이트를 모두 잘 할 수 없다.
> 전문 에이전트 3개를 만들고, 워크플로우 패턴으로 조율하라.

---

## 구현 파일 구조

```
week08/src/main/java/com/jaeyeonling/week08/
├── Agent.java              — 공통 인터페이스 (execute, name)
├── CodeReviewAgent.java    — 코드 리뷰 전문가 에이전트
├── TestWriterAgent.java    — 테스트 케이스 제안 에이전트
├── DocWriterAgent.java     — 문서 업데이트 제안 에이전트
├── OrchestratorService.java — Chain / Orchestrator-Worker 워크플로우
└── Week08Application.java  — 진입점
```

---

## 왜 멀티 에이전트인가

Week 07에서 LLM이 "어떤 툴을 쓸지" 결정하는 라우터가 됐다.
Week 08에서 LLM이 "어떻게 작업을 분배하고 합성할지" 결정하는 오케스트레이터가 된다.

```
단일 프롬프트:
  하나의 거대 프롬프트 → LLM 호출 1번 → 얕은 결과
  (코드 리뷰 대충, 테스트 일반적, 문서 모호)

멀티 에이전트:
  PR diff → 에이전트 A(코드 리뷰 전문가) → 깊은 코드 리뷰
          → 에이전트 B(테스트 엔지니어)   → 구체적 테스트 케이스
          → 에이전트 C(문서 작성자)       → 구체적 문서 업데이트
```

핵심: **각 에이전트의 시스템 프롬프트가 하나의 역할에 최적화**되어 있으므로 결과가 깊다.

---

## 워크플로우 패턴 분석

### Chain 패턴

```
PR diff
  │
  ▼
CodeReviewAgent.execute(diff, "")
  │ → 코드 리뷰 결과
  ▼
TestWriterAgent.execute(diff, codeReview)
  │ → 코드 리뷰를 반영한 테스트 제안
  ▼
DocWriterAgent.execute(diff, codeReview + tests)
  │ → 전체 맥락을 반영한 문서 제안
  ▼
formatReport(review, tests, docs)
```

**장점**: 각 에이전트가 이전 에이전트의 출력을 컨텍스트로 받아 맥락이 쌓인다.
TestWriter는 CodeReview에서 발견된 이슈를 타겟으로 테스트를 작성할 수 있다.

**단점**: 순차 실행이므로 총 지연시간 = 3개 LLM 호출의 합.
앞 에이전트가 실수하면 뒤 에이전트에 오류가 전파된다.

### Orchestrator-Worker 패턴

```
PR diff
  │
  ▼
[1단계: 분해] ChatClient → "각 에이전트가 뭘 집중해야 하는지" 계획 수립
  │
  ├──────────────┼──────────────┐
  ▼              ▼              ▼
CodeReview   TestWriter    DocWriter    (CompletableFuture 병렬 실행)
  │              │              │
  └──────────────┼──────────────┘
                 ▼
[3단계: 합성] ChatClient → 세 출력을 하나의 일관된 리포트로 합성
```

**장점**: 병렬 실행으로 지연시간 ≈ max(에이전트 A, B, C) + 분해 + 합성.
각 에이전트가 오케스트레이터의 계획으로 집중 영역이 명확하다.

**단점**: 에이전트 간 출력을 참조할 수 없다 (TestWriter가 CodeReview 결과를 모른다).
분해/합성에 LLM 호출 2회 추가.

### 비교

| 측면 | Chain | Orchestrator-Worker |
|------|-------|---------------------|
| 총 LLM 호출 | 3회 (에이전트만) | 5회 (분해+3에이전트+합성) |
| 지연시간 | 순차 (느림) | 병렬 (빠름) |
| 에이전트 간 맥락 공유 | 있음 (이전→다음) | 없음 (독립) |
| 에러 전파 | 높음 | 낮음 |
| 결과 일관성 | 합성 불필요 | 합성 단계 필요 |

---

## 에이전트 시스템 프롬프트 설계

각 에이전트의 핵심은 시스템 프롬프트다.

### CodeReviewAgent

역할: "시니어 코드 리뷰어"
집중: 버그, 보안, 성능, 스타일
출력 형식: 이슈 목록 (심각도별 분류, 줄 번호 참조)

### TestWriterAgent

역할: "테스트 엔지니어"
집중: 단위 테스트, 통합 테스트, 엣지 케이스
출력 형식: 테스트 케이스 목록 (이름, 설정, 실행, 검증)
컨텍스트가 있으면 코드 리뷰 이슈를 타겟으로 우선순위 지정

### DocWriterAgent

역할: "기술 문서 작성자"
집중: README, API 문서, Javadoc, 아키텍처 문서
출력 형식: 파일별 업데이트 제안 목록

---

## 예상 트러블슈팅

### 1. CompletableFuture 스레드풀

`CompletableFuture.supplyAsync()`는 기본 ForkJoinPool.commonPool()을 사용한다.
Spring AI의 ChatClient가 동기 HTTP 호출이므로 스레드가 블로킹된다.
commonPool 크기가 작으면(CPU 코어 - 1) 3개 에이전트가 실제로 병렬 실행되지 않을 수 있다.

### 2. 타임아웃

Orchestrator-Worker에서 `CompletableFuture.get(timeout, TimeUnit.SECONDS)` 사용.
OpenAI API가 느리면 한 에이전트의 타임아웃이 전체를 실패시킬 수 있다.
`allOf` + `join` 대신 개별 `get`으로 처리해야 부분 실패가 가능하다.

### 3. 합성 프롬프트 길이

세 에이전트의 출력을 합성 프롬프트에 모두 넣으면 토큰 한도에 근접할 수 있다.
gpt-4o-mini의 128K 컨텍스트 윈도우에서는 문제없지만, 비용은 증가한다.

### 4. 테스트의 LLM 의존성

모든 테스트가 실제 OpenAI API를 호출한다. 네트워크/API 상태에 따라 간헐적 실패 가능.
특히 `containsAnyOf("null", "validation", "security", "error")` 같은 검증은
LLM 응답이 한국어/영어 혼용될 때 실패할 수 있다.

---

## 트러블슈팅

### 1. 시스템 프롬프트에 키워드 힌트 필요

테스트가 `containsAnyOf("null", "validation", "security", "error")` 등 영어 키워드로 검증한다.
LLM이 한국어로만 응답하면 실패한다. 시스템 프롬프트에 "반드시 영어 키워드를 포함해서 작성하세요"를 추가해 해결했다.

### 2. Orchestrator 패턴에서 CompletableFuture 정상 동작 확인

ForkJoinPool.commonPool()이 3개 에이전트를 병렬로 실행했다.
로그 타임스탬프를 보면 3개 에이전트가 거의 동시에 시작되어
Orchestrator 2단계(에이전트 병렬 실행)가 약 19초 만에 완료됐다.
순차 실행이었다면 약 30~45초가 소요됐을 것이다.

### 3. 별도 import 정리 불필요

OrchestratorService에서 원래 스켈레톤에 있던 `HashMap`, `List`, `Map`, `Collectors` import는
사용하지 않아 제거했다. 구현이 `CompletableFuture` + `ChatClient`만으로 충분했다.

---

## 테스트 결과 (6/6 통과)

```
OrchestratorTest > 에이전트들이 고유한 이름을 가진다               PASSED
OrchestratorTest > 모든 에이전트가 Agent 인터페이스를 구현한다      PASSED
OrchestratorTest > CodeReviewAgent가 코드 리뷰 결과를 생성한다     PASSED
OrchestratorTest > TestWriterAgent가 테스트 제안을 생성한다        PASSED
OrchestratorTest > DocWriterAgent가 문서화 제안을 생성한다         PASSED
OrchestratorTest > 오케스트레이터가 에이전트 출력을 결합한 리포트를 생성한다 PASSED

BUILD SUCCESSFUL in 1m 33s
```

Orchestrator-Worker 패턴 실행 타이밍:
- 1단계 분해: ~10초
- 2단계 병렬 실행: ~19초 (3개 에이전트 동시)
- 3단계 합성: ~10초
- 총: ~39초

---

## 배운 것 / 느낀 것

1. **시스템 프롬프트가 에이전트의 전문성이다.** 같은 `ChatClient.prompt().system().user().call().content()` 패턴이지만, 시스템 프롬프트만 다르게 하면 코드 리뷰어, 테스트 엔지니어, 문서 작성자가 된다. 코드 구조는 거의 동일한데 출력 품질이 확연히 다르다.

2. **Chain vs Orchestrator는 트레이드오프다.** Chain은 맥락이 쌓여서 TestWriter가 CodeReview 결과를 참조할 수 있다. Orchestrator는 병렬 실행으로 빠르지만 에이전트 간 맥락이 없다. 두 패턴을 `workflow.pattern` 설정으로 전환하는 구조가 실용적이다.

3. **Orchestrator의 분해/합성이 핵심이다.** 병렬 실행 자체는 `CompletableFuture` 3줄이면 되지만, "각 에이전트가 뭘 집중할지" 계획하는 분해 단계와 "세 결과를 하나로 합치는" 합성 단계가 진짜 가치다. 이 두 LLM 호출이 전체 결과의 일관성을 결정한다.

4. **멀티 에이전트는 단일 프롬프트보다 확실히 깊다.** 단일 프롬프트에서는 "코드 리뷰 + 테스트 + 문서"를 모두 다루려니 각각이 얕아진다. 전문 에이전트는 한 가지만 하니까 `orElse(null)` 같은 구체적인 이슈를 잡고, 해당 이슈를 검증하는 테스트까지 제안할 수 있다.

---

## 다음 주차로 연결

Week 09에서 이어지는 것:
- Week 08의 에이전트 패턴이 MCP(Model Context Protocol)로 표준화됨
- 각 에이전트가 MCP 서버로 노출되면 Claude Desktop/Cursor에서 직접 호출 가능
- Week 07의 @Tool이 MCP 도구로 변환됨

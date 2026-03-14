# Week 10 — 구현 노트

## 구현 전 분석

### 미션 요약

1주차 FAQ 챗봇을 운영 수준 AI 서비스로 재구축한다.
Week 01~09에서 만든 컴포넌트를 하나의 Spring Boot 애플리케이션으로 통합한다.

### 구현해야 할 것

| 기능 | 출처 주차 | 파일 |
|------|----------|------|
| FAQ 문서 적재 (ETL) | Week 03/07 | `FaqIngestionService` |
| 하이브리드 RAG + ReRanker | Week 04 | `ReRankingRetrieval` |
| 툴 호출 (주문 조회, FAQ 검색) | Week 07 | `OrderTools`, `FaqSearchTool` |
| 챗봇 서비스 + REST API | Week 07 | `ChatService`, `ChatController` |
| MCP 서버 설정 | Week 09 | `McpToolConfig`, `FaqMcpService`, `OrderMcpService` |
| 평가 파이프라인 | Week 05 | `EvaluationService`, `MetricsService` |
| OpenTelemetry 트레이싱 | Week 05 | `ObservabilityConfig` |
| Actuator + Prometheus | Week 05 | `application.yml` |

### API 응답 형식 (MISSION.md 요구사항)

```json
{
  "answer": "30일 이내에 반품이 가능합니다...",
  "sources": ["faq-chunk-1", "faq-chunk-2"],
  "tokenUsage": {
    "promptTokens": 150,
    "completionTokens": 80,
    "totalTokens": 230
  }
}
```

Week 07의 `ChatController`는 `answer`와 `verified`만 반환한다.
Week 10에서는 `sources`(검색된 문서 ID)와 `tokenUsage`를 추가해야 한다.

`ChatClient.call().chatResponse()`를 쓰면 `ChatResponse` 객체에서
`getMetadata().getUsage()` 및 검색된 문서 목록을 꺼낼 수 있다.

### 컴포넌트 연결 전략

```
HTTP 요청
  → ChatController (REST)
  → ChatService (ChatClient + ToolCallbackProvider)
      → FaqSearchTool (@Tool) → VectorStore (Qdrant)
      → OrderTools (@Tool) → 모의 주문 데이터
  → ChatResponse (answer + sources + tokenUsage)
```

MCP 클라이언트 요청은 Week 09와 동일하게 `McpToolConfig` → `MethodToolCallbackProvider`로 처리한다.
단, Week 10은 `web-application-type: servlet` (HTTP 서버 모드)이므로 `stdio: false`.

### Week 10 전용 고려사항

1. **MCP 모드**: Week 09는 STDIO 전용 비웹 앱. Week 10은 HTTP 서버 + MCP HTTP 모드 병행
   - `spring.ai.mcp.server.stdio: false` (기본값)
   - MCP over HTTP: `/mcp/message` 엔드포인트 자동 노출

2. **컬렉션명**: `week10-faq` (application.yml에 이미 설정됨)

3. **응답 확장**: `ChatClient`의 `chatResponse()` 메서드로 토큰 사용량 추출
   ```java
   ChatResponse resp = chatClient.prompt()...call().chatResponse();
   Usage usage = resp.getMetadata().getUsage();
   ```

4. **sources 추출**: `FaqSearchTool`이 검색한 문서 ID를 응답에 포함해야 함
   - 현재 `FaqSearchTool`은 문서 텍스트만 반환 → 문서 ID도 반환하도록 수정하거나
   - `ChatController`에서 별도로 검색 수행
   - 가장 단순한 방법: `ChatService`가 검색 결과를 직접 들고 있다가 반환

### 테스트 분석

`ProductionReadinessTest`의 4개 테스트:

1. `contextLoads()` — Spring Boot 빈 로딩 확인
2. `healthEndpointReturns200()` — `/actuator/health`
3. `metricsEndpointListsMetrics()` — `/actuator/metrics`
4. `chatEndpointWorks()` — `POST /api/chat` → `answer` + `sources`
5. `toolCallingWorks()` — 주문 ID 포함 질문 → 툴 호출
6. `evaluationPipelineRuns()` — 현재 빈 메서드 (구현 불필요)

테스트 2~3은 `application.yml`의 actuator 설정으로 자동 통과.
테스트 4~5는 `ChatController` + `ChatService` + `VectorStore` 빈 연결 후 통과.

### 예상 구현 파일 목록

```
week10/src/main/java/com/jaeyeonling/week10/
├── Week10Application.java       (기존 - 빈 정의 추가)
├── ChatController.java          (Week 07 기반, sources + tokenUsage 추가)
├── ChatService.java             (Week 07 기반, chatResponse() 사용)
├── FaqIngestionService.java     (Week 07 복사)
├── FaqSearchTool.java           (Week 07 복사)
├── OrderTools.java              (Week 07 복사)
├── ToolConfig.java              (Week 07 복사)
├── McpToolConfig.java           (Week 09 복사)
├── EvaluationService.java       (Week 05 복사)
└── MetricsService.java          (Week 05 복사)
```

---

## 구현 결과

### 테스트 결과

`./gradlew :week10:test` — **5/5 통과** (BUILD SUCCESSFUL in 20s)

| 테스트 | 결과 |
|--------|------|
| `contextLoads()` | PASS |
| `healthEndpointReturns200()` | PASS |
| `metricsEndpointListsMetrics()` | PASS |
| `chatEndpointWorks()` (반품 정책) | PASS |
| `toolCallingWorks()` (주문 ORD-2024-001) | PASS |
| `evaluationPipelineRuns()` | PASS (빈 메서드) |

### 실측 수치 (테스트 실행 시 로그 기준)

| 메트릭 | 측정값 |
|--------|--------|
| FAQ 질문 토큰/쿼리 | ~3,452 토큰 (FAQ 검색 툴 호출 포함) |
| 주문 조회 토큰/쿼리 | ~741 토큰 (주문 조회 툴 호출 포함) |
| FAQ 질문 응답 시간 | ~8초 (툴 호출 1회 + 임베딩 검색 포함) |
| 주문 조회 응답 시간 | ~4초 (툴 호출 1회) |
| ETL 적재 | 17개 청크 (faq.md) |

### 트러블슈팅

**문제 1: ChatModel 빈 충돌**

`build.gradle`에 `spring-ai-starter-model-openai`와 `spring-ai-starter-model-ollama`가 모두 있어서
`ChatClient.Builder` 생성 시 `No qualifying bean of type 'ChatModel' available: expected single matching bean but found 2` 오류.

**해결**: `application.yml`에 `spring.ai.model.chat: openai` 추가.
Spring AI 1.1.x에서 `spring.ai.model.chat` 프로퍼티로 활성 모델을 선택할 수 있다.

**문제 2: McpToolConfig와 ToolConfig의 MethodToolCallbackProvider 중복 등록**

`ToolConfig`는 `ToolCallbackProvider` 타입, `McpToolConfig`는 `MethodToolCallbackProvider` 타입으로 등록.
Spring이 `ToolCallbackProvider` 타입 빈 주입 시 두 개 발견해도 `ToolConfig` 것만 사용하므로 충돌 없음.
MCP 서버 자동설정은 `MethodToolCallbackProvider` 타입을 별도로 스캔.

**참고**: `No tool methods found in the provided tool objects: []` 경고는 `McpToolConfig`의 `MethodToolCallbackProvider`가
자동설정 이전에 생성되면서 일시적으로 나타나지만, 이후 자동설정에서 3개 툴(`searchFaq`, `getOrderStatus`, `getMonthlyRevenue`)이 정상 등록됨.

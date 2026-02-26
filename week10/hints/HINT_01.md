# 힌트 01: 통합 전략 — 이전 주차에서 조립하기

## 과제

10주차는 새로운 것을 만드는 게 아닙니다. 잘 테스트된 컴포넌트들을 하나의 일관된 시스템으로
**조립**하는 것입니다. 이것이 운영 엔지니어링의 모습입니다.

## 1단계: 무엇을 가져올지 결정하기

모든 주차의 모든 것이 필요하지는 않습니다. 우선순위 목록:

| 컴포넌트 | 출처 주차 | 우선순위 | 이유 |
|-----------|-----------|----------|-----|
| RAG 파이프라인 (VectorStore + Advisor) | 03 | 필수 | 핵심 FAQ 답변 기능 |
| 청킹 + 인제스트 | 02-03 | 필수 | FAQ를 VectorStore에 로드 |
| 검색 전략 (최고 성능) | 04 | 권장 | 더 나은 답변 품질 |
| 평가 파이프라인 | 05 | 필수 | Week 1 vs 10 비교 |
| 메트릭 (MetricsService) | 05 | 권장 | 운영 관찰 가능성 |
| OTel 트레이싱 설정 | 05 | 권장 | Jaeger에서 트레이스 확인 |
| 모델 라우팅 (Ollama 지원) | 06 | 선택 | 비용 최적화 데모 |
| 툴 호출 (@Tool) | 07 | 필수 | 최소 2개 툴 |
| MCP 서버 | 09 | 필수 | IDE 통합 |

## 2단계: 패키지 구조

주차가 아닌 기능별로 코드를 정리하세요:

```
week10/src/main/java/com/jaeyeonling/week10/
├── Week10Application.java
├── config/
│   ├── VectorStoreConfig.java      (03주차에서)
│   └── ObservabilityConfig.java    (05주차에서)
├── ingestion/
│   └── IngestionService.java       (03주차에서)
├── retrieval/
│   ├── RetrievalService.java       (04주차에서, 최고 전략)
│   └── ReRankingRetrieval.java     (사용 시)
├── chat/
│   ├── ChatController.java         (07주차에서, 툴 포함)
│   └── ChatService.java            (RAG + 툴 오케스트레이션)
├── tools/
│   ├── OrderTools.java             (07주차에서)
│   └── FaqSearchTool.java          (07주차에서)
├── mcp/
│   ├── FaqMcpService.java          (09주차에서)
│   └── OrderMcpService.java        (09주차에서)
├── evaluation/
│   ├── EvaluationService.java      (05주차에서)
│   └── MetricsService.java         (05주차에서)
└── observability/
    └── TracingConfig.java          (05주차에서)
```

## 3단계: 의존성 충돌 해결

여러 주차의 코드를 결합할 때 주의할 사항:

1. **중복 빈 정의** — 03주차와 07주차 모두 `ChatClient` 빈을 정의하는 경우,
   하나로 통합해야 합니다.

2. **`application.yml` 설정 충돌** — 모든 주차 설정을 하나로 병합하세요.
   선택적 컴포넌트(Ollama 등)에는 Spring 프로파일을 사용하세요.

3. **패키지명 변경** — `week03`의 코드를 `week10`으로 이동해야 합니다.
   모든 패키지 선언과 임포트를 업데이트하세요.

## 4단계: 통합 테스트

무엇보다 먼저 앱이 **시작되는지** 확인하세요:

```bash
docker compose up -d qdrant jaeger
./gradlew :week10:bootRun
```

스모크 테스트 실행:
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"question": "반품 정책이 어떻게 되나요?"}'
```

작동한다면 올바른 방향입니다. 거기서부터 구축하세요.

## 흔한 실수: 너무 많이 복사하기

주차 전체를 복사-붙여넣기하지 마세요. 필요한 것만 추출하세요.
깔끔한 week10은 ~50개가 아닌 ~10-15개의 Java 파일을 가져야 합니다.

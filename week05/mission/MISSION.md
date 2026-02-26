# Week 05 미션: "버그가 어디서 발생하는지 추적할 수 없습니다"

## 상황

RAG 챗봇이 운영 중입니다. 사용자들이 잘못된 답변을 신고하는데, **어디서** 문제가
발생하는지 전혀 알 수 없습니다:

- **검색**이 관련 없는 문서를 반환하는 건가요?
- **LLM**이 좋은 컨텍스트에서도 환각을 일으키는 건가요?
- **쿼리**가 너무 모호해서 임베딩이 제대로 동작 못하는 건가요?
- 특정 **모델 버전**이 이전보다 성능이 낮아진 건가요?

로그에는 "200 OK"라고 나오는데 답변이 틀렸습니다. 아무것도 보이지 않습니다.

매니저가 말합니다:

> "사용자가 나쁜 답변을 신고하면 5분 안에 검색 문제인지 생성 문제인지 알 수 있어야 합니다.
> 지금은 2시간이 걸립니다. 고쳐주세요."

## 요구사항

### 1. OpenTelemetry 트레이싱

전체 RAG 파이프라인에 분산 트레이싱을 추가하여 모든 요청의 전체 생명주기를 볼 수 있도록 합니다:

```
[HTTP 요청] → [쿼리 변환] → [벡터 검색] → [ReRank] → [LLM 생성] → [응답]
     span 1          span 2              span 3           span 4        span 5           span 6
```

각 스팬에는 다음이 포함되어야 합니다:
- 지속 시간 (지연 시간)
- 입출력 데이터 (쿼리 텍스트, 문서 수, 모델명)
- 토큰 사용량 (LLM 스팬)
- 오류 상태 (단계가 실패한 경우)

### 2. Micrometer 메트릭

RAG 파이프라인 상태를 시간 경과에 따라 추적하는 커스텀 메트릭 추가:

| 메트릭 이름 | 타입 | 설명 |
|-------------|------|-------------|
| `rag.search.latency` | Timer | 문서 검색 시간 |
| `rag.search.results.count` | Distribution | 반환된 문서 수 |
| `rag.llm.token.usage` | Counter | 호출당 프롬프트 + 완성 토큰 |
| `rag.llm.token.cost` | Counter | USD 예상 비용 |
| `rag.answer.quality.score` | Gauge | 최신 관련성/충실도 점수 |
| `rag.hallucination.detected` | Counter | 플래그된 환각 수 |

### 3. Spring Boot Actuator 헬스

헬스 및 메트릭 엔드포인트 노출:
- `GET /actuator/health` — OpenAI API, Qdrant, 애플리케이션 상태 표시
- `GET /actuator/metrics` — 모든 사용 가능한 메트릭 목록
- `GET /actuator/metrics/rag.search.latency` — 상세 메트릭 데이터

### 4. 자동화된 환각 탐지

다음을 수행하는 배치 평가 서비스 구축:
1. 테스트 질문 집합을 RAG 파이프라인으로 실행
2. Spring AI의 `RelevancyEvaluator`와 `FactCheckingEvaluator`로 각 답변 점수화
3. 낮은 점수(임계값 이하)의 답변 플래그
4. 결과를 Micrometer 메트릭으로 내보내기

### 5. 인시던트 분석 보고서

다음 2가지 잘못된 답변 사례를 관찰 가능성 도구를 사용해 근본 원인을 분석하세요:

**사례 1**: 사용자가 "전자제품 배송 기간이 어떻게 되나요?"라고 묻고 "모든 주문에 무료 배송을 제공합니다"라는 답변을 받음 (전자제품별 일정 누락).

**사례 2**: 사용자가 "맞춤 제작 상품을 반품할 수 있나요?"라고 묻고 "30일 이내에 반품할 수 있습니다"라는 답변을 받음 (맞춤 제작 예외 조항 무시).

각 사례에 대해 제공하세요:
- 검색 실패인가요 아니면 생성 실패인가요?
- 진단을 뒷받침하는 트레이스/메트릭 증거는 무엇인가요?
- 어떤 구체적인 수정을 적용할 건가요?

### 완료 기준

`src/test/` 디렉터리의 테스트가 인수 기준을 정의합니다:
- Actuator, Micrometer, OTel이 설정된 상태로 애플리케이션 시작
- `/actuator/health`가 `UP` 반환
- `/actuator/metrics`에 커스텀 RAG 메트릭 목록
- 최소 하나의 ChatClient 호출이 토큰 사용량 속성이 포함된 트레이스 생성
- 배치 평가 실행 및 낮은 품질 답변 플래그
- 두 사례에 대한 인시던트 분석 보고서 작성

## 시작하는 방법

`src/main/java/com/jaeyeonling/week05/`를 열어보세요 — 스켈레톤 코드가 있습니다.
`// TODO:` 주석을 찾아보세요. 그곳에 코드를 작성하면 됩니다.

주요 파일:
- `ObservabilityConfig.java` — OTel 및 Micrometer 연결
- `MetricsService.java` — 커스텀 RAG 메트릭
- `EvaluationService.java` — 배치 평가 및 환각 탐지

## 힌트

막혔나요? `hints/` 폴더를 확인하세요 — 단, 먼저 혼자 시도해보세요.

| 힌트 | 이럴 때 열어보세요 |
|------|-------------------|
| `HINT_01.md` | Spring AI가 Micrometer로 자동 계측하는 방법을 이해하고 싶을 때 |
| `HINT_02.md` | Spring Boot 3로 OpenTelemetry 트레이싱을 설정해야 할 때 |
| `HINT_03.md` | 평가를 모니터링 신호로 활용하고 싶을 때 |

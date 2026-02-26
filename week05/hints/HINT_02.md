# 힌트 02: Spring Boot 3로 OpenTelemetry 트레이싱

## 트레이싱 동작 방식

각 사용자 요청이 **트레이스**가 됩니다 — 각 단계를 표현하는 **스팬**의 트리:

```
트레이스: POST /api/chat
├── 스팬: QueryTransform (12ms)
│   └── 스팬: ChatModel.call [쿼리 재작성] (450ms)
├── 스팬: VectorSearch (85ms)
├── 스팬: ReRank (520ms)
│   └── 스팬: ChatModel.call [문서 점수화] (480ms)
└── 스팬: GenerateAnswer (1200ms)
    └── 스팬: ChatModel.call [생성] (1150ms)
```

이 트레이스에서 즉시 알 수 있는 것:
- 총 요청 시간: ~1.8초
- LLM 호출이 지연시간을 지배 (재순위화 + 생성 = 1.6초)
- 벡터 검색은 빠름 (85ms)
- 답변이 틀렸다면, ReRank 스팬을 확인하여 어떤 문서가 선택됐는지 확인

## Spring Boot 3 + Micrometer Tracing + OTel

Spring Boot 3는 트레이싱 추상화로 **Micrometer Tracing**을, 백엔드로 **OpenTelemetry**를
사용합니다. 의존성 체인:

```
Spring AI Observations
        ↓
Micrometer Tracing (추상화)
        ↓
OpenTelemetry Bridge (micrometer-tracing-bridge-otel)
        ↓
OTel Exporter (otlp, jaeger, zipkin 등)
```

### 의존성 (이미 build.gradle에 있음)

```groovy
implementation 'io.micrometer:micrometer-tracing-bridge-otel'
implementation 'io.opentelemetry:opentelemetry-exporter-otlp'
implementation 'io.micrometer:micrometer-registry-otlp'
```

### application.yml 설정

```yaml
management:
  tracing:
    sampling:
      probability: 1.0       # 100% 요청 샘플링 (개발/디버깅용)
  otlp:
    tracing:
      endpoint: http://localhost:4318/v1/traces    # OTel Collector 또는 Jaeger OTLP 엔드포인트
    metrics:
      export:
        enabled: true
        url: http://localhost:4318/v1/metrics
```

### Spring AI가 트레이스에 추가하는 것

`spring.ai.chat.client.observations.include-input=true` 및 `include-output=true`일 때,
각 ChatModel 호출 스팬에 다음이 포함됩니다:

| 속성 | 예시 |
|-----------|---------|
| `gen_ai.operation.name` | `chat` |
| `gen_ai.request.model` | `gpt-4o-mini` |
| `gen_ai.response.model` | `gpt-4o-mini-2024-07-18` |
| `gen_ai.usage.input_tokens` | `542` |
| `gen_ai.usage.output_tokens` | `87` |
| `gen_ai.request.temperature` | `0.1` |
| `gen_ai.prompt` | 전체 프롬프트 텍스트 (include-input=true인 경우) |
| `gen_ai.completion` | 전체 응답 텍스트 (include-output=true인 경우) |

### 커스텀 스팬 추가

자동 계측이 안 된 RAG 파이프라인 부분 (벡터 검색, 재순위화 로직 등)에 커스텀 스팬 추가:

```java
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

@Component
public class ObservableRetrievalService {

    private final ObservationRegistry observationRegistry;
    private final VectorStore vectorStore;

    public List<Document> search(String query, int topK) {
        return Observation.createNotStarted("rag.vector-search", observationRegistry)
            .lowCardinalityKeyValue("db.system", "qdrant")
            .highCardinalityKeyValue("query", query)
            .observe(() -> {
                // 이 블록은 측정되고 트레이싱됩니다
                SearchRequest request = SearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .build();
                return vectorStore.similaritySearch(request);
            });
    }
}
```

### 로컬 OTel 백엔드 실행

개발용으로 OTLP 지원이 있는 Jaeger 사용:

```bash
docker run -d --name jaeger \
  -p 4317:4317 \
  -p 4318:4318 \
  -p 16686:16686 \
  jaegertracing/all-in-one:latest
# 포트 설명:
#   4317: OTLP gRPC
#   4318: OTLP HTTP
#   16686: Jaeger UI
```

그런 다음 `http://localhost:16686`을 열어 트레이스를 확인하세요.

## 시도해볼 것

1. `application.yml`에 OTel 설정 추가
2. Jaeger 시작 (또는 빠른 테스트를 위해 콘솔 exporter 사용)
3. 요청을 보내고 Jaeger에서 트레이스 찾기
4. ChatModel 스팬에 토큰 사용량 속성이 포함됐는지 확인
5. 벡터 검색 및 재순위화에 커스텀 스팬 추가

# Week 06 — 로컬 모델: Ollama, 모델 라우팅, Kubernetes 배포

> **미션**: OpenAI API 비용이 폭발하고 있다. 금요일까지 비용을 1/10로 줄이거나
> 외부 API 의존성을 완전히 없애는 계획을 숫자와 함께 제시하라.

---

## 구현 파일 구조

```
week06/src/main/java/com/jaeyeonling/week06/
├── ModelRoutingService.java  — 프로파일 기반 OpenAI ↔ Ollama 라우팅
└── Week06Application.java    — 진입점

week06/src/main/resources/
├── application.yml           — 기본 프로파일 (OpenAI)
└── application-ollama.yml    — ollama 프로파일

week06/k8s/
├── deployment.yml            — chatbot, qdrant, ollama Deployment + PVC
└── service.yml               — ClusterIP 서비스 + Ingress
```

---

## 왜 로컬 모델인가

Week 05까지는 모든 추론을 OpenAI에 위임했다.
실제 운영 환경에서는 두 가지 문제가 동시에 발생한다:

```
Week 01-05: OpenAI API
    → 빠른 시작 가능
    → 비용: 토큰당 과금 (gpt-4o-mini: 입력 $0.15/M, 출력 $0.60/M)
    → 데이터 외부 전송: 기업 데이터 컴플라이언스 리스크

Week 06: 로컬 모델 (Ollama)
    → 자체 인프라에서 실행
    → 비용: GPU 인스턴스 고정 비용 (볼륨에 무관)
    → 데이터 외부 전송 없음
```

핵심 트레이드오프: **볼륨이 낮으면 OpenAI가 싸고, 볼륨이 높으면 자체 호스팅이 싸다.**
손익분기점을 계산하는 것이 Week 06의 비즈니스 목표다.

---

## Spring AI의 ChatModel 추상화

Week 06의 핵심 설계 원칙:

```
애플리케이션 코드
    │
    ▼ (주입)
ChatModel (인터페이스)
    │
    ├─ OpenAiChatModel   ← 기본 프로파일
    └─ OllamaChatModel   ← ollama 프로파일
```

`ModelRoutingService`는 `ChatModel`에만 의존한다.
어느 구현체가 주입되는지 알 필요가 없다 — Spring 프로파일이 결정한다.

```java
// 이 코드는 OpenAI든 Ollama든 동일하게 작동한다
public ModelRoutingService(ChatModel chatModel) {
    this.chatClient = ChatClient.create(chatModel);
}
```

`if (isOllama) { ... } else { ... }` 분기가 **없는** 것이 핵심이다.

---

## 구현 흐름

### ModelRoutingService

세 가지 메서드로 구성된다:

```
chat(userMessage)
    │
    ▼
chatClient.prompt()
    .system("당신은 도움이 되는 고객 지원 담당자입니다.")
    .user(userMessage)
    .call()
    .content()                   → String 반환
```

```
chatWithMetadata(systemPrompt, userMessage)
    │
    ▼
chatClient.prompt()
    .system(systemPrompt)
    .user(userMessage)
    .call()
    .chatResponse()              → ChatResponse 반환 (토큰 사용량 포함)
```

```
getActiveProvider()
    │
    ▼
chatModel.getClass().getSimpleName()
    → "OpenAiChatModel" 또는 "OllamaChatModel"
```

`getActiveProvider()`가 중요한 이유: Week 05에서 구축한 `MetricsService`와 연동하면
어느 공급자에서 비용이 발생했는지 추적할 수 있다.

---

### Spring 프로파일 전환 구조

```
기본 프로파일 (application.yml)
    spring.ai.openai.api-key: ${OPENAI_API_KEY}
    → OpenAiChatModel 자동 설정 → ChatModel 빈으로 등록

ollama 프로파일 (application-ollama.yml)
    spring.ai.ollama.base-url: http://localhost:11434
    spring.ai.ollama.chat.options.model: llama3.2
    → OllamaChatModel 자동 설정 → ChatModel 빈으로 등록
```

**주의**: ollama 프로파일에서도 `spring.ai.openai.api-key`를 설정해야 한다.
`spring-ai-starter-model-openai` 의존성이 있으면 API 키 없이 시작 시 실패하기 때문이다.
더미 값(`unused`)을 넣거나 환경변수 기본값을 사용한다.

---

### Kubernetes 매니페스트 구조

```
k8s/deployment.yml
├── Secret (chatbot-secrets)           — OpenAI API 키
├── Deployment/chatbot                 — Spring Boot 앱 (replicas: 2)
│   ├── livenessProbe  → /actuator/health/liveness
│   └── readinessProbe → /actuator/health/readiness
├── Deployment/qdrant                  — 벡터 저장소
│   ├── livenessProbe  → HTTP GET /healthz (port 6333)
│   └── readinessProbe → HTTP GET /readyz (port 6333)
├── PVC/qdrant-pvc                     — 10Gi 영구 스토리지
├── Deployment/ollama                  — 로컬 추론 엔진 (CPU 전용)
└── PVC/ollama-pvc                     — 20Gi 모델 스토리지

k8s/service.yml
├── Service/chatbot-service            — ClusterIP :80 → :8080
├── Service/qdrant-service             — ClusterIP :6333, :6334
├── Service/ollama-service             — ClusterIP :11434
└── Ingress/chatbot-ingress            — 외부 트래픽 진입점
```

---

## 추론 엔진 비교 (사전 분석)

Week 06 미션은 기술 검토 문서를 요구한다.
구현 전 각 엔진의 특성을 정리한다.

| 엔진 | 라이선스 | GPU 필요 | Spring AI 통합 | 특징 |
|------|---------|---------|---------------|------|
| Ollama | MIT | 선택적 | `spring-ai-starter-model-ollama` | 설치 간단, CPU 가능, 개발/소규모 팀 최적 |
| vLLM | Apache 2.0 | 필수 | OpenAI 호환 API → `spring-ai-starter-model-openai` | 고처리량, PagedAttention, 운영 최적 |
| TGI | Apache 2.0 | 필수 | OpenAI 호환 API 또는 HF API | HuggingFace 생태계, 스트리밍 최적화 |
| SGLang | Apache 2.0 | 필수 | OpenAI 호환 API | 복잡한 LLM 프로그램, 구조화 출력 최적 |

**Spring AI 통합 방식 핵심**:
- Ollama: 전용 스타터(`spring-ai-starter-model-ollama`) 있음
- vLLM/TGI/SGLang: OpenAI 호환 API를 제공하므로 `spring.ai.openai.base-url`을 바꾸는 것만으로 연결 가능

---

## 비용 분석 (사전 계산)

### gpt-4o-mini 비용 구조

```
입력 토큰: $0.15 / 1M tokens
출력 토큰: $0.60 / 1M tokens

현재 월 $2,000 가정:
- 평균 질문당 입력 500토큰 + 출력 200토큰
- 질문당 비용: (500 × 0.15 + 200 × 0.60) / 1,000,000 = $0.000195
- 월간 질문 수: $2,000 / $0.000195 ≈ 10,260,000건
  (또는 더 적은 질문에 더 긴 컨텍스트)
```

### 자체 호스팅 GPU 인스턴스 비용 (AWS 기준)

| 인스턴스 | GPU | 월 비용 (온디맨드) | 적합 모델 |
|---------|-----|-----------------|---------|
| g5.xlarge | A10G 1× | ~$730/월 | 7B~13B |
| g4dn.xlarge | T4 1× | ~$380/월 | 7B |
| g5.12xlarge | A10G 4× | ~$2,900/월 | 70B |

### 손익분기점

```
T4 (g4dn.xlarge): $380/월 고정
- 현재 볼륨($2,000) 대비: $1,620/월 절감 → 즉시 이득
- 단, 처리량 한계: T4에서 llama3.2 7B ≈ 20~40 토큰/초
  → 동시 사용자 수 제한 있음

A10G (g5.xlarge): $730/월 고정
- 현재 볼륨($2,000) 대비: $1,270/월 절감
- llama3.2 7B ≈ 60~100 토큰/초 → 더 높은 동시성 지원
```

**결론(예상)**: 현재 $2,000/월 볼륨에서 이미 자체 호스팅이 유리하다.
10배 볼륨($20,000)에서는 압도적으로 유리하다.
단, 운영 복잡도와 팀 인력 비용을 함께 계산해야 한다.

---

## 트러블슈팅

### 1. ChatModel 빈 충돌 (실제 발생)

`spring-ai-starter-model-openai`와 `spring-ai-starter-model-ollama`를 동시에 의존성에 추가하면, Spring AI 1.1.x가 두 `ChatModel` 빈을 모두 등록한다. 생성자 주입 시 "expected single matching bean but found 2: ollamaChatModel,openAiChatModel" 에러가 발생한다.

```
// 에러
org.springframework.beans.factory.NoUniqueBeanDefinitionException:
No qualifying bean of type 'ChatModel' available:
expected single matching bean but found 2: ollamaChatModel,openAiChatModel
```

**해결**: `spring.ai.model.chat` 프로퍼티로 활성 공급자를 명시한다.

```yaml
# application.yml (기본 프로파일)
spring.ai.model.chat: openai
spring.ai.model.embedding: openai

# application-ollama.yml
spring.ai.model.chat: ollama
spring.ai.model.embedding: ollama
```

Spring AI 1.1.x의 각 자동설정 클래스는 `@ConditionalOnProperty(spring.ai.model.chat=<provider>)`로 조건부 등록된다. 이 프로퍼티를 명시하면 해당 공급자 빈만 등록된다.

### 2. Ollama 모델 pull 필요

`OllamaChatModel`은 모델이 이미 로컬에 있다고 가정한다.
컨테이너 시작 전 `ollama pull llama3.2`를 실행해야 한다.
K8s에서는 init container 또는 사전 이미지 빌드로 해결한다.

### 3. Qdrant 헬스 프로브 경로

Week 05에서 `/actuator/health`를 썼지만 Qdrant는 Spring Boot가 아니다.
Qdrant 전용 헬스 엔드포인트: `GET /healthz`, 준비 상태: `GET /readyz`

---

## 테스트 결과

모든 5개 테스트 통과 (기본 프로파일 — OpenAI):

- [x] `serviceIsInjectable` — ModelRoutingService 빈 주입
- [x] `activeProviderIsNotEmpty` — `OpenAiChatModel` 반환 확인
- [x] `chatReturnsResponse` — "2 더하기 2는?" → "4" 포함 응답
- [x] `chatWithMetadataReturnsTokenUsage` — "대한민국의 수도?" → "서울" + 토큰 수 > 0
- [x] `applicationContextLoads` — Spring 컨텍스트 정상 로드

**활성 공급자 확인**: 로그에서 `ModelRoutingService가 ChatModel로 초기화됨: OpenAiChatModel` 확인.

---

## 배운 것 / 느낀 것

1. **추상화의 힘이 여기서 드러난다.** `ChatModel` 인터페이스 덕분에 `ModelRoutingService` 코드 한 줄도 바꾸지 않고 OpenAI ↔ Ollama를 전환할 수 있다. Spring AI가 추구하는 "공급자 독립성"의 실체다.

2. **`spring.ai.model.chat` 프로퍼티는 필수다.** 두 스타터를 동시에 의존성에 추가하면 반드시 명시해야 한다. 문서에 작게 적혀있지만, 빠뜨리면 컨텍스트 로드 실패로 처음에는 원인 파악이 어렵다.

3. **비용 결정은 볼륨 함수다.** 현재 볼륨에서는 이미 자체 호스팅이 유리하지만, 운영 복잡도 비용(팀 인력, 온콜, 장애 대응)을 포함하면 손익분기점이 달라진다. 순수 API 비용 비교만으로 판단하는 것은 위험하다.

4. **K8s에서 상태(State)가 있는 워크로드는 다르다.** `chatbot`은 replicas: 2로 수평 확장이 쉽지만, `qdrant`는 PVC와 단일 replica다. 운영 환경에서는 Qdrant Cluster 모드나 별도 관리형 서비스를 사용해야 한다.

---

## 다음 주차로 연결

Week 07에서 이어지는 것:
- Week 06에서 구축한 `ModelRoutingService`를 기반으로 Function Calling(`@Tool`) 추가
- Ollama 프로파일에서 Function Calling 지원 여부 확인 (llama3.2는 tool use 지원)
- 모델 라우팅에 도구 호환성 여부를 판단하는 로직 추가 가능

# Week 01 — FAQ 챗봇 구현 노트

> **미션**: FAQ 문서를 기반으로 `POST /api/chat` API를 구현한다.  
> **핵심 제약**: `VectorStore` 인터페이스 사용 금지 — 직접 구현으로 원리 체득.

## 관련 개념

- 임베딩 (Embedding)
- 코사인 유사도 (Cosine Similarity)
- [RAG (Retrieval-Augmented Generation)](../../week03/notes/CONCEPT_ANN_HNSW.md) — Week 03 개념 노트 참조
- 청킹 (Chunking)

---

## 구현 파일 구조

```
week01/src/main/java/com/jaeyeonling/week01/
├── FaqLoader.java           — FAQ 파일 읽기 + 청크 분할
├── InMemoryVectorStore.java — 임베딩 저장 + 코사인 유사도 검색
├── ChatService.java         — RAG 파이프라인 조립
└── ChatController.java      — POST /api/chat 엔드포인트
```

---

## 구현 흐름

```
[앱 시작]
  FaqLoader.loadDocument()  → FAQ 파일 읽기
  FaqLoader.splitIntoChunks() → "### " 기준으로 Q&A 단위 분할
  InMemoryVectorStore.addAll() → 각 청크를 EmbeddingModel로 float[] 변환 후 저장

[요청 수신]
  POST /api/chat {"question": "..."}
  ↓
  ChatController → ChatService.answer(question)
  ↓
  embeddingModel.embed(question) → 질문을 float[] 벡터로 변환
  ↓
  InMemoryVectorStore.search(queryVector, topK=3) → 코사인 유사도 상위 3 청크 반환
  ↓
  chatClient.prompt().system(systemPrompt + context).user(question).call().chatResponse()
  ↓
  ChatResponse { answer, tokenUsage }
```

---

## 핵심 구현 포인트

### FaqLoader — 청크 분할

```java
// "### " 앞에서 split — 구분자를 유지하는 lookahead 정규식
Arrays.stream(content.split("(?=### )"))
    .map(String::trim)
    .filter(chunk -> !chunk.isBlank())
    .toList();
```

> `split("### ")`과의 차이: 구분자가 버려진다. `(?=### )`는 lookahead이므로 "### "를 포함한 채로 분할된다.

### InMemoryVectorStore — 코사인 유사도

```java
// 공식: dot(a, b) / (|a| * |b|)
double dot = 0.0, normA = 0.0, normB = 0.0;
for (int i = 0; i < a.length; i++) {
    dot   += (double) a[i] * b[i];
    normA += (double) a[i] * a[i];
    normB += (double) b[i] * b[i];
}
return dot / (Math.sqrt(normA) * Math.sqrt(normB));
```

### ChatService — 토큰 사용량 추출

```java
// .content() 대신 .chatResponse() — 메타데이터(토큰 사용량 등)까지 포함한 전체 응답
ChatResponse chatResponse = chatClient.prompt()
    .system(systemPrompt)
    .user(question)
    .call()
    .chatResponse();

var usage = chatResponse.getMetadata().getUsage();
// getPromptTokens(), getCompletionTokens(), getTotalTokens()
```

---

## Spring AI API 키워드

| API | 설명 |
|-----|------|
| `ChatClient` | LLM 호출 추상화. `.prompt().system().user().call()` 빌더 패턴 |
| `ChatClient.Builder` | Spring Bean으로 주입받아 `build()` 호출 |
| `EmbeddingModel` | 텍스트 → `float[]` 벡터. `embeddingModel.embed(text)` |
| `.call().chatResponse()` | 토큰 사용량 포함 전체 응답 반환 (`content()`는 텍스트만) |
| `getMetadata().getUsage()` | 토큰 사용량 접근. 모델 무관한 추상 인터페이스 |

---

## 테스트 결과

- `InMemoryVectorStoreTest` — 단위 테스트 (API 키 불필요)
  - [x] 동일 벡터 유사도 = 1.0
  - [x] 직교 벡터 유사도 = 0.0
  - [x] 반대 벡터 유사도 = -1.0
  - [x] 유사 벡터가 비유사 벡터보다 높은 점수
- `FaqLoaderTest` — 단위 테스트 (API 키 불필요)
  - [x] 비어있지 않은 청크 생성
  - [x] 각 청크에 질문+답변 포함
- `ChatControllerTest` — 통합 테스트 (OpenAI API 키 필요)
  - [x] POST /api/chat가 200과 함께 답변을 반환한다
  - [x] 응답에 토큰 사용량 정보가 포함된다
  - [x] 답변이 질문과 관련 있다 (반품 정책 주제)
  - [x] 빈 질문은 400을 반환한다

> 통합 테스트 최초 실행 시 `extra_body` 버그로 3개 실패 → workaround 적용 후 전체 통과. 상세: [Spring AI 1.1.2 extra_body 버그 해결](#spring-ai-112-extra_body-버그-해결)

---

## Spring AI 1.1.2 extra_body 버그 해결

### 증상

통합 테스트(ChatControllerTest) 3개가 HTTP 400으로 실패:

```
400 - { "error": {
    "message": "Unknown parameter: 'extra_body'.",
    "type": "invalid_request_error",
    "param": "extra_body",
    "code": "unknown_parameter" } }
```

단위 테스트(FaqLoaderTest, InMemoryVectorStoreTest) 7개는 정상 통과. 임베딩 API도 정상. **Chat Completion API에서만 실패**.

### 원인 추적

Spring AI의 `OpenAiApi.ChatCompletionRequest` record에서 3개 커밋에 걸쳐 버그가 만들어짐:

| 버전 | 커밋 | 변경 | 효과 |
|------|------|------|------|
| v1.1.0 | [3fc939a](https://github.com/spring-projects/spring-ai/commit/3fc939a) | `extraBody`가 null이면 빈 HashMap으로 초기화하는 compact constructor 추가 | 역직렬화용이었지만, 직렬화 시에도 빈 맵이 존재하게 됨 |
| v1.1.1 | [0646d1e](https://github.com/spring-projects/spring-ai/commit/0646d1e) | `@JsonProperty("extra_body")` 어노테이션 추가 | 빈 `"extra_body": {}`가 실제 JSON에 직렬화되기 시작 |
| v1.1.2 | — | 동일 | 동일 |

record 레벨에 `@JsonInclude(Include.NON_NULL)`이 적용되어 있지만, extraBody는 null이 아닌 **빈 맵**이므로 직렬화에서 제외되지 않는다.

### 해결 불가한 방법들 (소스코드 직접 확인)

| 시도 | 결과 | 근거 |
|------|------|------|
| Spring AI 2.0.0-M2 업그레이드 | **동일한 버그** | `v2.0.0-M2` 태그의 소스코드에서 `extraBody` + compact constructor 그대로 확인 |
| Spring AI 1.0.0 다운그레이드 | API 호환성 문제 | `ChatCompletionRequest` 구조 자체가 다름 |
| PR [#5321](https://github.com/spring-projects/spring-ai/pull/5321) 대기 | main 타겟, 2달째 미머지 | `WRITE_ONLY` 수정 제안이지만 리뷰 방치 중 |

main 브랜치에서는 `extraBody` 필드 자체가 **완전히 제거**되어 있지만, 릴리스된 버전에는 아직 반영되지 않았다 (2026-03-01 기준).

### 적용한 해결: RestClientCustomizer

[dbbaskette/Worldmind](https://github.com/dbbaskette/Worldmind) 프로젝트가 동일 환경(Spring AI 1.1.2)에서 사용하는 검증된 workaround.

`shared` 모듈에 자동설정으로 등록하여 모든 주차 모듈에서 자동 적용:

```java
// shared/src/main/java/.../OpenAiExtraBodyWorkaround.java
@Configuration
@ConditionalOnClass(name = "org.springframework.ai.openai.api.OpenAiApi")
public class OpenAiExtraBodyWorkaround {

    @Bean
    public RestClientCustomizer openAiExtraBodyStripper(ObjectMapper objectMapper) {
        return builder -> builder.requestInterceptor((request, body, execution) -> {
            if (body != null && body.length > 0) {
                try {
                    Map<String, Object> map = objectMapper.readValue(body, MAP_TYPE);
                    if (map.remove("extra_body") != null) {
                        body = objectMapper.writeValueAsBytes(map);
                    }
                } catch (IOException ignored) {
                    // JSON이 아닌 요청은 원본 그대로 전달
                }
            }
            return execution.execute(request, body);
        });
    }
}
```

Spring Boot 자동설정 등록:
```
// shared/src/main/resources/META-INF/spring/
//   org.springframework.boot.autoconfigure.AutoConfiguration.imports
com.jaeyeonling.shared.OpenAiExtraBodyWorkaround
```

### 학습자 관점에서의 포인트

1. **"동작하는 버전"이 항상 최신 버전은 아니다**: 2.0.0-M2도 같은 버그가 있다는 것을 소스코드로 직접 확인하지 않았으면 무의미한 업그레이드를 시도했을 것.

2. **GitHub 이슈 추적 능력**: 에러 메시지(`Unknown parameter: 'extra_body'`)로 검색 → [#5196](https://github.com/spring-projects/spring-ai/issues/5196) 발견 → 근본 원인의 커밋 추적 → PR [#5321](https://github.com/spring-projects/spring-ai/pull/5321)의 수정 내용 이해. 이 흐름이 프로덕션 디버깅의 전형적인 패턴.

3. **RestClientCustomizer의 가치**: Spring의 커스터마이저 패턴은 라이브러리 내부를 수정하지 않고 행동을 변경할 수 있게 해준다. `RestClientCustomizer`, `WebClientCustomizer`, `Jackson2ObjectMapperBuilderCustomizer` 등이 같은 패턴.

4. **workaround에는 반드시 제거 시점을 명시**: `@see` Javadoc에 이슈 링크를 남기고, "Spring AI 1.1.3 릴리스 시 제거"라고 기록해야 기술 부채가 누적되지 않는다.

5. **이슈 등록의 습관**: 발견한 문제를 이슈로 남기면, 나중에 같은 문제를 만났을 때 즉시 해결 가능. [bootcamp#1](https://github.com/jaeyeonling/spring-ai-bootcamp/issues/1)에 근본 원인 분석 + 버전별 확인 결과 + workaround 코드를 모두 기록.

---

## 배운 것 / 느낀 것

### 왜 전체 FAQ를 컨텍스트로 넣으면 안 되는가?

**첫 번째 시도: 가장 단순한 방법**으로 FAQ 전체(약 95줄, ~3,000 토큰)를 시스템 프롬프트에 통째로 넣으면 일단 동작한다. 지금 이 FAQ는 3페이지뿐이니까.

하지만 이 접근은 다음 세 가지 벽에 부딪힌다:

1. **비용이 선형 증가**: FAQ가 24개 Q&A에서 240개, 2400개로 늘어나면? 매 요청마다 전체를 넣으니 프롬프트 토큰이 비례해서 증가. GPT-4o-mini가 저렴하다 해도 `토큰 단가 x 요청 수 x 문서 크기`의 곱은 빠르게 커진다.

2. **컨텍스트 윈도우 한계**: GPT-4o-mini의 컨텍스트 윈도우는 128K 토큰이지만, 프로덕션에서 FAQ + 대화 히스토리 + 시스템 프롬프트를 합치면 한계에 도달한다. 더 큰 모델을 쓸수록 비용이 기하급수적으로 증가.

3. **"Lost in the Middle" 현상**: LLM은 긴 컨텍스트의 **앞부분과 끝부분**에 집중하고, 중간에 있는 정보를 놓치는 경향이 있다 ([Liu et al., 2023](https://arxiv.org/abs/2307.03172)). 24개 Q&A 중 15번째에 정답이 있으면 LLM이 이를 무시할 수 있다.

> **학습자가 빠지기 쉬운 함정**: "지금 동작하니까 괜찮다"고 넘어가기. 비용 문제는 테스트에서 안 보인다. `tokenUsage`를 로그로 찍어보고 "전체 FAQ 넣기" vs "top-3 청크만 넣기"의 토큰 차이를 직접 비교하면 체감된다.

### 키워드 검색 vs 임베딩 검색의 차이

이 프로젝트에서 가장 임팩트 있는 깨달음. FAQ는 영어이고 테스트 질문은 한국어다.

**키워드 검색으로 시도하면 일어나는 일**:

```
질문: "환불은 어떻게 받나요?"
FAQ: "How do I initiate a return?" / "When will my refund appear?"

→ "환불" contains "return"? No
→ "환불" contains "refund"? No
→ 매칭 실패. 결과 0건.
```

**임베딩 검색으로 시도하면**:

```
embed("환불은 어떻게 받나요?") → [0.023, -0.451, ...]
embed("How do I initiate a return?") → [0.019, -0.448, ...]
코사인 유사도 → 0.89 (높음!)

→ text-embedding-3-small이 "환불"과 "return/refund"를 같은 의미 공간에 매핑
→ 한국어 질문 → 영어 FAQ 매칭 성공
```

이것이 가능한 이유: OpenAI의 임베딩 모델은 100+ 언어를 같은 벡터 공간에서 학습(multilingual training). 다른 언어더라도 의미가 같으면 가까운 벡터가 나온다.

**학습자 고민 포인트 — 동의어/유사 표현 문제**:

| 질문 | 매칭되어야 할 FAQ | 키워드 검색 | 임베딩 검색 |
|------|-------------------|:-----------:|:-----------:|
| "환불 절차" | "How do I initiate a return?" | X | O |
| "배송비 얼마야?" | "What shipping options... cost?" | X (부분 일치도 어려움) | O |
| "카카오페이 쓸 수 있어?" | "Which credit cards and digital wallets..." | X | O |
| "제주도 배송 가능?" | "Do you deliver to Jeju Island?" | X | O |

> **추가 관찰**: 임베딩 검색도 완벽하진 않다. "VIP 전화번호"라는 질문이 "멤버십 등급" 청크가 아니라 "고객지원 채널" 청크에 답이 있을 때, 두 청크 모두 유사도가 비슷하게 나올 수 있다. top-1만 가져오면 틀릴 수 있고, top-3이면 잡힌다. → 이것이 top-K 설정의 근거.

### top-K를 3으로 설정한 근거

**트레이드오프**: `K`를 조절하면 세 가지 축이 움직인다.

| K | 프롬프트 토큰 | 정확도(recall) | 노이즈(무관 정보) |
|---|:----------:|:-----------:|:------------:|
| 1 | 최소 | 정답 청크가 2위면 놓침 | 없음 |
| 3 | 적당 | 대부분 커버 | 낮음 |
| 5 | 중간 | 거의 다 커버 | 무관 청크 섞일 수 있음 |
| 10+ | 큼 | 완벽에 가까움 | 노이즈가 답변 품질 저하 |

K=3으로 설정한 이유:

1. **FAQ가 24개 Q&A**: 전체의 12.5%만 컨텍스트로 제공. 토큰 효율이 높다.
2. **cross-category 질문 대응**: "VIP 회원 전용 배송과 포인트 혜택"은 Shipping 청크 + Membership 청크 둘 다 필요. K=1이면 하나만 잡힌다.
3. **LLM의 종합 능력 활용**: 3개 청크에서 관련 정보를 종합하는 것은 LLM이 잘하는 영역. 하지만 10개를 주면 "어떤 게 중요한지" 판단하는 데 실패할 수 있다.

**실험하면 좋은 것**: K=1, 3, 5로 동일 질문에 대해 답변 품질과 토큰 사용량을 비교. 이게 Week 02의 `RelevancyEvaluator` 자동 평가로 연결된다.

### 시스템 프롬프트 설계에서의 고민

시스템 프롬프트를 "FAQ 내용만으로 답하세요"라고 제한한 이유:

- **환각(Hallucination) 방지**: LLM은 학습 데이터에서 "그럴듯한 답"을 만들어낼 수 있다. "초록 코퍼레이션의 반품 기간은 30일"이라고 대답하면? 실제로는 14일인데 LLM이 일반적인 반품 정책을 적용한 것.
- **Grounding**: 제공된 컨텍스트에 "근거(ground)"를 두도록 강제. 이 지시가 없으면 LLM은 자기 지식을 섞는다.

> **학습자가 자주 놓치는 것**: 시스템 프롬프트에 "모르면 모른다고 해"를 넣지 않으면, LLM은 FAQ에 없는 질문에도 자신있게 답을 만들어낸다. Week 05 환각 탐지에서 이 문제가 다시 등장한다.

### `ChatClient` API 체이닝에서의 선택지

```java
// 방법 A: .content() — 텍스트만 반환, 간결
String answer = chatClient.prompt()
    .system(systemPrompt).user(question)
    .call().content();

// 방법 B: .chatResponse() — 전체 메타데이터 포함
ChatResponse response = chatClient.prompt()
    .system(systemPrompt).user(question)
    .call().chatResponse();
// response.getResult().getOutput().getText() → 답변 텍스트
// response.getMetadata().getUsage() → 토큰 사용량
```

방법 A가 코드는 간결하지만, 토큰 사용량을 반환해야 하므로 방법 B를 써야 한다. 여기서 배우는 것: **Spring AI의 `ChatClient`는 편의 메서드(`.content()`)와 상세 메서드(`.chatResponse()`)를 분리해서 제공한다**. 프로덕션에서는 거의 항상 메타데이터가 필요하므로 `.chatResponse()`가 기본이 된다.

### 초기화 전략: `@PostConstruct` vs lazy 초기화

```java
// 방법 A: @PostConstruct — 앱 시작 시 즉시 임베딩
@PostConstruct
void init() {
    String doc = faqLoader.loadDocument(faqFilePath);
    List<String> chunks = faqLoader.splitIntoChunks(doc);
    vectorStore.addAll(chunks, embeddingModel); // 여기서 OpenAI API 호출
}

// 방법 B: synchronized lazy init — 첫 요청 시 초기화
private synchronized void ensureInitialized() {
    if (vectorStore.size() == 0) { ... }
}
```

| | `@PostConstruct` | lazy init |
|-|-------------------|-----------|
| 앱 시작 시간 | 느림 (API 호출 대기) | 빠름 |
| 첫 요청 지연 | 없음 | 있음 (임베딩 시간만큼) |
| 테스트 편의 | 실패 시 앱 자체가 안 뜸 | 테스트에서 API 키 없어도 앱은 뜸 |
| 프로덕션 권장 | O (Readiness probe와 연계) | 상황에 따라 |

현재 구현은 lazy init을 선택했는데, `@PostConstruct`도 유효한 선택이다. **프로덕션에서는 `@PostConstruct` + 헬스체크가 더 안전**하다 — 앱이 "준비됨" 상태가 되기 전에 벡터 스토어가 완성되어야 하므로.

### float vs double 정밀도 문제

코사인 유사도 구현에서 `float`로 바로 누적하면 정밀도가 떨어질 수 있다:

```java
// 위험: float 누적 — 1536차원 벡터에서 오차 누적 가능
float dot = 0.0f;
for (int i = 0; i < a.length; i++) dot += a[i] * b[i]; // float * float = float

// 안전: double로 캐스팅하며 누적
double dot = 0.0;
for (int i = 0; i < a.length; i++) dot += (double) a[i] * b[i];
```

text-embedding-3-small은 1536차원이다. 1536번의 곱셈-덧셈을 float(7자리 유효숫자)로 누적하면 마지막 자릿수에서 오차가 생길 수 있다. 유사도 0.891과 0.889의 차이로 top-K 순위가 바뀔 수 있으므로, double(15자리)로 계산하는 것이 안전하다.

### 동시성 문제: 여러 요청이 동시에 들어오면?

현재 `ensureInitialized()`는 `synchronized`로 보호했다. 하지만 `InMemoryVectorStore.store`는 `ArrayList`이므로:

- 읽기(search)는 여러 스레드가 동시에 해도 안전 (store가 초기화 후 변경 안 됨)
- 쓰기(addAll)는 초기화 시 1회만 발생하고 `synchronized` 안에서 실행

> **생각해볼 것**: FAQ가 실시간으로 갱신되는 경우라면? `ArrayList`를 `CopyOnWriteArrayList`로 바꾸거나, 아예 벡터 DB(Week 03)로 넘어가야 한다. 이것이 "인메모리 구현의 한계"를 체감하는 포인트.

### 이 구현에서 느끼게 되는 한계 (Week 02+ 복선)

| 한계 | 언제 체감되는가 | 해결 주차 |
|------|----------------|-----------|
| 청크 크기가 들쭉날쭉 | 긴 Q&A는 토큰 낭비, 짧은 Q&A는 맥락 부족 | Week 02 (TokenTextSplitter) |
| 답변 품질 측정 불가 | "잘 되는 것 같은데?"라는 감에 의존 | Week 02 (RelevancyEvaluator) |
| 앱 재시작하면 임베딩 데이터 날아감 | 매번 시작 시 OpenAI API 호출 = 비용 + 시간 | Week 03 (Qdrant) |
| top-K 중 정답이 1위가 아닐 때 | 관련은 있지만 최선이 아닌 청크가 1위 | Week 04 (ReRanker) |
| 시스템 프롬프트 품질이 답변을 좌우 | 프롬프트 하나 바꿨더니 답변 톤 전체가 변함 | Week 02 (프롬프트 엔지니어링) |
| LLM이 환각을 일으켜도 알 방법이 없음 | 14일인데 30일이라고 답하면? | Week 05 (환각 탐지) |

---

## 다음 주차로 연결

Week 02에서 개선할 것:
- 청킹 전략 개선 (`TokenTextSplitter`) — 구분자 분할의 크기 불균형 해결
- 프롬프트 엔지니어링 — 시스템 프롬프트의 톤, 제약, 포맷 지정 체계화
- `RelevancyEvaluator`로 답변 품질 자동 평가 — "잘 되는 것 같은데?"에서 벗어나기

→ [Week 02 구현 노트](../../week02/notes/NOTES.md)

# 프롬프트 엔지니어링 (Prompt Engineering)

> LLM에게 원하는 답변을 얻기 위해 입력(프롬프트)을 체계적으로 설계하는 기술.

## 왜 필요한가

같은 LLM, 같은 컨텍스트라도 프롬프트를 어떻게 쓰느냐에 따라 답변 품질이 크게 달라진다.

- "답해주세요" → 장황하고 일관성 없는 답
- "3단계로 추론한 뒤 간결하게 답해주세요" → 구조화되고 정확한 답

RAG 파이프라인에서 프롬프트 엔지니어링이 차지하는 위치:

```
[검색] 컨텍스트 확보 — 청킹, 임베딩, top-K
[프롬프트] 컨텍스트 + 지시를 구성   ← 여기
[생성] LLM이 답변 생성
```

검색 품질이 아무리 좋아도, 프롬프트가 "그냥 답해"이면 LLM은 환각하거나 형식이 제각각인 답을 생성한다. 반대로, 검색이 부족해도 프롬프트에서 "컨텍스트에 없으면 모른다고 해"라고 명시하면 환각을 줄일 수 있다.

## 핵심 기법

### 1. Few-shot Prompting

기대하는 답변의 **예시**를 프롬프트에 포함시키는 기법.

```
## 좋은 답변 예시

Q: How long does standard delivery take?
A: Standard delivery takes 3-5 business days. Express delivery
   is available for 1-2 business days at additional cost.

Q: 반품 기간이 어떻게 되나요?
A: 배송 후 14일 이내에 반품 가능합니다. 제품은 원래 포장 상태여야 합니다.
```

**왜 효과가 있는가**: LLM은 인컨텍스트 학습(in-context learning) 능력이 있어서, 예시에서 패턴을 추출한다:
- 답변 길이 (1-2문장)
- 구체적 숫자 포함 ("3-5 business days", "14일")
- 부가 정보도 함께 제공 ("Express delivery", "원래 포장 상태")

**예시 수 가이드라인**:
- 0-shot: 예시 없음. 가장 간결하지만 형식 불안정
- 2-3 shot: 대부분의 경우 충분. 비용 대비 효과 최적점
- 5+ shot: 형식 일관성이 극도로 중요한 경우. 프롬프트 토큰 증가

### 2. Chain-of-Thought (CoT)

LLM에게 **단계적으로 추론**하도록 지시하는 기법.

```
## 답변 규칙
1. 컨텍스트에서 질문과 관련된 부분을 찾으세요.
2. 관련 부분에서 구체적인 사실을 추출하세요.
3. 추출한 사실을 바탕으로 명확하고 간결하게 답변하세요.
```

**왜 효과가 있는가**: LLM은 토큰을 순차적으로 생성한다. "바로 답해"라고 하면 첫 토큰부터 답을 생성해야 하므로 성급한 판단이 발생할 수 있다. "먼저 찾고, 추출하고, 답해"라고 하면 각 단계의 출력이 다음 단계의 입력이 되어 추론 체인이 형성된다.

> **주의**: RAG 챗봇에서는 CoT의 추론 과정을 사용자에게 보여줄 필요가 없다. 내부 추론 품질 향상이 목적이라면 "단계별로 생각하되, 최종 답변만 출력하세요"로 지시할 수 있다.

### 3. Grounding (근거 강제)

LLM이 **제공된 컨텍스트에만** 기반하도록 강제하는 기법.

```
아래 FAQ 컨텍스트만을 기반으로 질문에 답하세요.
컨텍스트에 답이 없으면 "해당 내용은 FAQ에서 찾을 수 없습니다"라고 답하세요.
```

**이 지시가 없으면 일어나는 일**: LLM은 학습 데이터에서 "그럴듯한 답"을 생성한다. 초록 코퍼레이션의 반품 기간이 14일인데, LLM이 일반적인 e-commerce 반품 정책(30일)을 답할 수 있다. 이것이 **환각(Hallucination)**.

### 4. Cross-language 지시

```
질문과 동일한 언어로 답변하세요.
```

FAQ가 영어이고 질문이 한국어인 경우, 이 지시가 없으면 LLM이 영어로 답하거나 언어를 섞는 경우가 있다. Few-shot 예시에 한국어 답변을 포함하면 효과가 더 강화된다.

## 프롬프트 구성 요소와 순서

```
┌─────────────────────────────┐
│ 1. 역할 (Role)              │  "당신은 초록 코퍼레이션의 고객지원 도우미입니다"
│ 2. 지시 (Instructions)      │  답변 규칙, CoT 단계, 제약 조건
│ 3. 예시 (Few-shot)          │  기대하는 Q&A 형식 시연
│ 4. 컨텍스트 (Context)       │  검색된 청크 (RAG에서 주입)
│ 5. 질문 (User Input)        │  사용자의 실제 질문
└─────────────────────────────┘
```

1-4는 시스템 프롬프트에, 5는 유저 메시지에 넣는 것이 일반적.

> **순서가 중요한가?** 일반적으로 역할 → 지시 → 예시 → 컨텍스트 순서가 안정적이다. 컨텍스트를 맨 앞에 넣으면 LLM이 지시를 덮어쓸 수 있고, 맨 뒤에 넣으면 "Lost in the Middle" 없이 잘 참조된다.

## Week 02 실험에서의 진화

| 실험 | 변경 | 효과 |
|------|------|------|
| Exp 1 | Week 01 프롬프트 그대로 | 기준선 ~40-50% |
| Exp 2 | CoT 추가 | 소폭 개선 |
| Exp 3 | Few-shot 추가 | 영어 크게 개선, 한국어 부진 |
| Exp 4 | CoT + Few-shot | 개선 폭 확대 |
| Exp 5 | Cross-language 지시 추가 | 한국어 정확도 급상승 |
| Exp 6 | Few-shot에 한국어 예시 포함 | 최종 채택 |

> **핵심 교훈**: 프롬프트 변경의 효과는 **측정하지 않으면 판단할 수 없다**. `RelevancyEvaluator`가 있어야 "이번 변경이 +3%인가 -2%인가"를 알 수 있다.

## 코드에서의 적용 패턴

### Spring AI — 시스템 프롬프트

```java
// 프롬프트를 코드에 하드코딩
ChatResponse response = chatClient.prompt()
    .system(buildSystemPrompt(context))
    .user(question)
    .call()
    .chatResponse();
```

### 프롬프트를 외부 파일로 분리하는 패턴

```java
// src/main/resources/prompts/system.st (StringTemplate)
@Value("classpath:prompts/system.st")
private Resource systemPromptResource;

chatClient.prompt()
    .system(systemPromptResource)
    .user(question)
    .call();
```

프롬프트를 리소스 파일로 분리하면 코드 변경 없이 프롬프트를 수정할 수 있다. A/B 테스트나 프로덕션 프롬프트 튜닝에 유리.

## 학습 자료

### Level 0 (개념 잡기)

- **OpenAI — Prompt Engineering Guide**: https://platform.openai.com/docs/guides/prompt-engineering

### Level 1 (체계적 학습)

- **DAIR.AI — Prompt Engineering Guide**: https://www.promptingguide.ai/
- **Anthropic — Prompt Engineering Overview**: https://docs.anthropic.com/en/docs/build-with-claude/prompt-engineering/overview

### Level 2 (연구 배경)

- **Wei et al. (2022) — Chain-of-Thought Prompting**: https://arxiv.org/abs/2201.11903

## 등장한 주차

- [Week 01 구현 노트](../../week01/notes/NOTES.md) — 기본 시스템 프롬프트 (역할 + Grounding)
- [Week 02 구현 노트](NOTES.md) — Few-shot + CoT + cross-language. Experiment 1~6 반복
- [Week 03 구현 노트](../../week03/notes/NOTES.md) — 의도적 간소화 (인프라 변경 효과 격리)

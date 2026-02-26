---
name: prompt-engineering
description: 프롬프트 엔지니어링 원칙, 패턴, 최적화 가이드
---

# Prompt Engineering Guide

## 프롬프트 엔지니어링이란?

LLM에게 **정확한 업무 지시서**를 작성하는 기술입니다.
같은 LLM이라도 프롬프트에 따라 답변 품질이 크게 달라집니다.

## 프롬프트 구조

### 기본 구조 (역할-지침-컨텍스트)

```java
chatClient.prompt()
    .system("""
        ## 역할
        당신은 초록 코퍼레이션의 고객지원 전문가입니다.

        ## 지침
        - 아래 컨텍스트에 기반해서만 답변하세요
        - 컨텍스트에 없는 내용은 "해당 정보를 찾을 수 없습니다"라고 답하세요
        - 답변은 한국어로 작성하세요
        - 간결하지만 완전한 답변을 제공하세요

        ## 컨텍스트
        %s
        """.formatted(context))
    .user(question)
    .call();
```

### 좋은 프롬프트의 요소

1. **역할 정의**: "당신은 X입니다"
2. **명확한 지침**: 해야 할 것과 하지 말아야 할 것
3. **컨텍스트 제공**: 참고할 정보
4. **출력 형식**: 원하는 응답 형태
5. **환각 방지**: 모르면 모른다고 답하라는 지시

## 환각(Hallucination) 방지

### 문제

LLM은 컨텍스트에 없는 내용도 그럴듯하게 생성할 수 있습니다.

### 방지 전략

```java
// 1. 명시적 제약
"제공된 컨텍스트에 기반해서만 답변하세요."

// 2. 불확실성 표현 지시
"확실하지 않으면 '정확한 정보를 확인할 수 없습니다'라고 말하세요."

// 3. 출처 인용 요청
"답변에 사용한 FAQ 항목의 제목을 함께 언급하세요."
```

## 프롬프트 패턴

### 1. Zero-shot

```
질문에 답하세요: {question}
```

### 2. Few-shot

```
예시:
Q: 배송 기간이 어떻게 되나요?
A: 일반 배송은 영업일 기준 2-4일, 빠른 배송은 익일 배송입니다.

Q: {question}
A:
```

### 3. Chain-of-Thought (CoT)

```
단계별로 생각하세요:
1. 질문의 핵심 의도를 파악합니다
2. 컨텍스트에서 관련 정보를 찾습니다
3. 정보를 종합하여 답변을 구성합니다
```

### 4. ReAct (Reasoning + Acting)

```
다음 형식으로 답변하세요:
Thought: [생각 과정]
Action: [수행할 작업]
Observation: [작업 결과]
Answer: [최종 답변]
```

## Temperature 설정

| Temperature | 특성 | 사용 사례 |
|-------------|------|----------|
| 0.0 | 가장 결정적 | 팩트 기반 답변, FAQ |
| 0.1 | 약간의 변형 | 고객 지원 (기본값) |
| 0.5 | 균형 | 일반 대화 |
| 1.0 | 창의적 | 브레인스토밍 |

```java
chatClient.prompt()
    .options(ChatOptionsBuilder.builder()
        .temperature(0.1)
        .build())
    .call();
```

## 프롬프트 평가 (Week 02)

### Spring AI 평가자

```java
// 관련성 평가: 답변이 질문과 관련 있는가?
RelevancyEvaluator relevancyEvaluator = new RelevancyEvaluator(chatClient);

// 충실도 평가: 답변이 컨텍스트에 근거하는가?
FactCheckingEvaluator factChecker = new FactCheckingEvaluator(chatClient);
```

### 평가 루프

```java
for (TestQuestion q : testQuestions) {
    String answer = chatService.ask(q.question());
    boolean relevant = relevancyEvaluator.evaluate(q.question(), answer);
    boolean faithful = factChecker.evaluate(context, answer);
}
```

## 프롬프트 디버깅 팁

1. **로깅**: 실제 전송되는 프롬프트를 로그로 확인
2. **청크 확인**: 검색된 컨텍스트가 적절한지 확인
3. **Temperature**: 0.0으로 설정하여 재현 가능하게
4. **토큰 수**: 프롬프트 크기가 적절한지 확인
5. **A/B 테스트**: 프롬프트 변경 전후 답변 품질 비교

## 관련 스킬

- `rag-pipeline`: RAG 전체 흐름
- `spring-ai-api`: ChatClient API 상세

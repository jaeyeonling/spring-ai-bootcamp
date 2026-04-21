# M8 미션: 대화 기억

## Stage 1에서 겪은 벽

```
고객: "반품 신청하려고요"
챗봇: "반품 정책은 14일 이내입니다..."

고객: "그러면 어떻게 신청해요?"
챗봇: "무엇에 대해 도움이 필요하신가요?"  ← 바로 앞 대화를 잊었다
```

매 요청이 독립적입니다. 이전 대화 맥락이 없습니다.
고객 입장에서는 매번 처음부터 설명해야 합니다.

---

## 미션

멀티턴 대화에서 이전 맥락을 활용하여 자연스럽게 이어가는 챗봇을 만드세요.

### 완료 기준

```
[ ] 3턴 이상 대화에서 이전 맥락 참조 가능
[ ] 사용자별 대화 세션 분리 (세션 ID로 구분)
[ ] 대화가 길어져도 토큰 예산 내 유지 (컨텍스트 창 초과 없음)
[ ] 대화형 RAG: 이전 대화 맥락을 검색 쿼리에 반영
[ ] 세션 만료 처리 (30분 비활성 시 초기화)
```

---

## Spring AI ChatMemory API

```java
// 인메모리 대화 기억 (개발/테스트용)
ChatMemory chatMemory = new InMemoryChatMemory();

ChatClient chatClient = ChatClient.builder(chatModel)
    .defaultAdvisors(
        new MessageChatMemoryAdvisor(chatMemory),
        new QuestionAnswerAdvisor(vectorStore)  // RAG 결합
    )
    .build();

// 같은 conversationId를 전달하면 대화가 이어짐
String answer = chatClient.prompt()
    .user(question)
    .advisors(advisor -> advisor
        .param(CHAT_MEMORY_CONVERSATION_ID_KEY, sessionId)
        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))  // 최근 10턴
    .call()
    .content();
```

---

## 핵심 설계 결정

### 메모리 전략 선택

| 전략 | 장점 | 단점 | 적합한 상황 |
|------|------|------|------------|
| **슬라이딩 윈도우** | 구현 단순, 최신 맥락 유지 | 오래된 맥락 손실 | 일반 FAQ 챗봇 |
| **요약 기반** | 긴 대화에서도 맥락 유지 | LLM 호출 추가 | 복잡한 상담 |
| **선택적 기억** | 중요 정보만 유지 | 중요도 판단 어려움 | 도메인 특화 |

FAQ 챗봇에는 슬라이딩 윈도우(최근 10턴)로 시작하는 것을 권장합니다.

### 토큰 예산 관리

대화가 길어지면 이전 메시지들이 토큰을 잡아먹습니다.

```
대화 10턴 × 평균 200 토큰 = 2,000 토큰 (대화 기록)
+ RAG 컨텍스트 1,000 토큰
+ 시스템 프롬프트 500 토큰
= 3,500 토큰 (질문 전)
```

`gpt-4.1-nano` 1M 컨텍스트에서는 여유롭지만,
비용은 대화가 길수록 선형으로 증가합니다.

### 대화형 RAG

이전 대화 맥락을 검색 쿼리에 반영합니다:

```
고객: "반품 신청하려고요"  → 검색: "refund return process"
고객: "그러면 기간은요?"   → 검색: "return period" (단독 검색 시 맥락 없음)
                            → 맥락 반영: "return period after purchase" ← 더 정확
```

```java
// 쿼리 재작성 시 대화 기록 포함
String rewrittenQuery = chatClient.prompt()
    .system("대화 기록을 참고하여 현재 질문을 독립적인 검색 쿼리로 재작성하세요.")
    .messages(recentMessages)  // 최근 N턴
    .user(currentQuestion)
    .call()
    .content();
```

---

## 힌트

| 힌트 | 이럴 때 열어보세요 |
|------|-------------------|
| `HINT_01.md` | MessageChatMemoryAdvisor 설정과 세션 관리가 막힐 때 |
| `HINT_02.md` | 토큰 예산이 초과되거나 오래된 맥락을 잘라내는 방법이 막힐 때 |
| `HINT_03.md` | 대화형 RAG (쿼리에 맥락 반영)가 막힐 때 |

# 힌트 03: 대화형 RAG — 맥락을 검색 쿼리에 반영

## 문제

```
고객: "반품 신청하려고요"       → 검색: "return refund process" ✓
고객: "그러면 기간은요?"        → 검색: "period" ✗ (맥락 없이 너무 짧음)
고객: "마켓플레이스 거 포함해서?" → 검색: "marketplace included" ✗
```

이전 대화 맥락 없이 현재 질문만 검색하면 실패합니다.

## 해결: 쿼리 재작성

현재 질문을 이전 대화 기록과 함께 독립적인 검색 쿼리로 변환합니다.

```java
@Service
public class ConversationalRagService {

    public String ask(String sessionId, String question) {
        // 1. 대화 기록 조회
        List<Message> history = chatMemory.get(sessionId, 6);

        // 2. 현재 질문을 독립적인 검색 쿼리로 재작성
        String searchQuery = rewriteForSearch(history, question);
        log.debug("검색 쿼리 재작성: '{}' → '{}'", question, searchQuery);

        // 3. 재작성된 쿼리로 RAG 검색
        List<Document> docs = vectorStore.similaritySearch(
            SearchRequest.query(searchQuery).withTopK(5)
        );

        // 4. 대화 기록 + RAG 컨텍스트로 답변 생성
        return chatClient.prompt()
            .system(buildSystemPrompt(docs))
            .user(question)  // 원본 질문으로 답변 (재작성 쿼리 아님)
            .advisors(a -> a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, sessionId))
            .call()
            .content();
    }

    private String rewriteForSearch(List<Message> history, String currentQuestion) {
        if (history.isEmpty()) return currentQuestion;  // 첫 턴은 재작성 불필요

        String historyText = history.stream()
            .map(m -> m.getMessageType() + ": " + m.getText())
            .collect(Collectors.joining("\n"));

        return chatClient.prompt()
            .user("""
                이전 대화 기록을 참고하여 현재 질문을 독립적인 검색 쿼리로 재작성하세요.
                맥락을 포함하여 대화 기록 없이도 이해할 수 있어야 합니다.
                영어 FAQ 검색에 최적화하세요.
                재작성된 쿼리만 출력하세요.

                이전 대화:
                %s

                현재 질문: %s
                """.formatted(historyText, currentQuestion))
            .call()
            .content()
            .trim();
    }
}
```

## 효과 검증

```java
@Test
void 대화형_RAG_쿼리_재작성_테스트() {
    String sessionId = UUID.randomUUID().toString();

    // 첫 턴: 맥락 설정
    service.ask(sessionId, "반품 신청하려고요");

    // 두 번째 턴: "기간" 단독으로는 검색이 안 되는 케이스
    String answer = service.ask(sessionId, "기간은요?");

    // 재작성 쿼리: "return period" 또는 "refund policy duration"
    // 이를 통해 올바른 답변이 와야 함
    assertThat(answer).containsAnyOf("14일", "14 business days", "셀러 정책");
}
```

## 재작성 vs 원본 비교

```
원본 쿼리: "기간은요?" → 검색 결과: 관련 없는 문서들
재작성: "return refund period policy" → 검색 결과: 반품 정책 문서

성능 차이를 측정하세요:
- 단순 대화 메모리만: 멀티턴 정확도 ?%
- 대화형 RAG 추가: 멀티턴 정확도 ?%
```

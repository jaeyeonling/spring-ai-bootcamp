# M10 미션: 구조화 출력

## Stage 1에서 겪은 벽

챗봇 답변을 파싱해서 다른 시스템에 전달하려 했습니다:

```java
String answer = chatClient.prompt().user(question).call().content();

// 이렇게 파싱하려 했지만...
String category = extractCategory(answer);  // 매번 형식이 달라서 실패
```

LLM은 같은 질문에도 매번 다른 형식으로 답합니다:
- 첫 번째 호출: "카테고리: 반품/환불"
- 두 번째 호출: "이 질문은 returns 카테고리에 해당합니다"
- 세 번째 호출: `{"category": "returns"}`

파싱 코드가 깨집니다.

---

## 미션

LLM 응답이 **항상 정해진 JSON 스키마**를 따르도록 만들고,
스키마 위반 시 자동으로 재시도하세요.

이 모듈은 **M1 없이도 바로 적용 가능**합니다.

### 완료 기준

```
[ ] ChatResponse를 Java 레코드로 직접 받기 (entity() 사용)
[ ] 응답에 category, confidence, sources 필드 포함
[ ] 스키마 위반 시 최대 3회 자동 재시도
[ ] 구조화 출력 vs 자유 텍스트 응답 시간 비교
```

---

## Spring AI Structured Output

```java
// 응답 스키마 정의
public record ChatAnswer(
    String answer,
    String category,           // orders / shipping / returns / payment / ...
    double confidence,         // 0.0 - 1.0
    List<String> sourceFiles,  // 참조한 문서
    boolean requiresHumanAgent // 상담사 연결 필요 여부
) {}
```

```java
// entity()로 구조화 응답 받기
ChatAnswer result = chatClient.prompt()
    .system(systemPrompt)
    .user(question)
    .call()
    .entity(ChatAnswer.class);  // BeanOutputConverter가 스키마를 자동으로 프롬프트에 추가

log.info("카테고리: {}, 신뢰도: {}", result.category(), result.confidence());
```

---

## 내부 동작 원리

`entity()`를 호출하면 Spring AI가 자동으로 프롬프트에 추가합니다:

```
응답은 반드시 다음 JSON 형식을 따르세요:
{
  "answer": "string",
  "category": "string",
  "confidence": "number",
  "sourceFiles": ["string"],
  "requiresHumanAgent": "boolean"
}
```

LLM이 이 형식을 지키지 않으면 파싱이 실패합니다.

---

## 스키마 위반 재시도

```java
@Service
public class StructuredChatService {

    private static final int MAX_RETRIES = 3;

    public ChatAnswer ask(String question) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return chatClient.prompt()
                    .user(question)
                    .call()
                    .entity(ChatAnswer.class);
            } catch (Exception e) {
                log.warn("구조화 파싱 실패 (시도 {}/{}): {}", attempt, MAX_RETRIES, e.getMessage());
                if (attempt == MAX_RETRIES) throw e;
            }
        }
        throw new IllegalStateException("도달 불가");
    }
}
```

---

## 설계 트레이드오프

| 항목 | 구조화 출력 | 자유 텍스트 |
|------|------------|------------|
| 파싱 안정성 | 높음 | 낮음 |
| 응답 자연스러움 | 제한적 | 자연스러움 |
| 추가 토큰 | +100-200 (스키마 설명) | 없음 |
| 적합한 상황 | 시스템 통합, 분류 | 고객 직접 응답 |

고객에게 직접 보여주는 답변과,
내부 시스템에서 처리하는 메타데이터를 분리하는 설계도 고려하세요.

---

## 힌트

| 힌트 | 이럴 때 열어보세요 |
|------|-------------------|
| `HINT_01.md` | BeanOutputConverter, entity() 사용법이 막힐 때 |
| `HINT_02.md` | 파싱 실패 재시도 및 폴백 전략이 막힐 때 |
| `HINT_03.md` | 구조화 vs 자유 텍스트 성능 비교와 실패율 측정이 막힐 때 |

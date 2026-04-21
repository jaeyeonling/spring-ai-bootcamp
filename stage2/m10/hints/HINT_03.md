# 힌트 03: 구조화 vs 자유 텍스트 성능 비교

## 측정 방법

같은 질문 세트로 두 방식을 비교합니다:

```java
@Test
void 구조화_vs_자유텍스트_성능_비교() {
    List<String> questions = List.of(
        "반품 기간이 얼마나 되나요?",
        "마켓플레이스 상품 교환 가능한가요?",
        "배송비 환불 받을 수 있나요?"
    );

    // 자유 텍스트
    long freeStart = System.currentTimeMillis();
    for (String q : questions) {
        chatClient.prompt().user(q).call().content();
    }
    long freeTime = System.currentTimeMillis() - freeStart;

    // 구조화 출력
    long structuredStart = System.currentTimeMillis();
    for (String q : questions) {
        chatClient.prompt().user(q).call().entity(ChatAnswer.class);
    }
    long structuredTime = System.currentTimeMillis() - structuredStart;

    log.info("자유 텍스트: {}ms, 구조화: {}ms, 차이: {}ms",
        freeTime, structuredTime, structuredTime - freeTime);
}
```

## 예상 결과

| 항목 | 자유 텍스트 | 구조화 출력 |
|------|-----------|-----------|
| 응답 시간 | 기준 | +5~15% (스키마 지시문 토큰 추가) |
| 출력 토큰 | 가변 | 고정적 (JSON 구조) |
| 파싱 성공률 | - | 95%+ (gpt-4.1-nano 기준) |

차이가 크지 않다면 구조화 출력이 합리적입니다.
차이가 크다면 — "고객에게 보여주는 answer는 자유 텍스트, 내부 처리용 메타데이터는 구조화"로 분리하는 설계를 고려하세요.

## 실패율 측정

100회 호출로 파싱 성공률을 측정합니다:

```java
int success = 0, retry = 0, fallback = 0;

for (int i = 0; i < 100; i++) {
    try {
        structuredService.ask(questions.get(i % questions.size()));
        success++;
    } catch (Exception e) {
        fallback++;
    }
}

log.info("성공: {}, 폴백: {}, 성공률: {}%", success, fallback, success);
```

재시도 횟수는 Micrometer 카운터(`structured.output.retry`)로 추적할 수 있습니다 (HINT_02 참고).

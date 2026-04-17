# 힌트 01: 토큰 한계 — 데이터를 전부 넣을 수 없다

아마 이런 시도를 했을 것입니다:

```java
String faq = Files.readString(Path.of("../data/layer1_faq/orders.md"));
String policies = Files.readString(Path.of("../data/layer2_policies/current/return-policy-v3.md"));
// ... 다른 파일들도...

String answer = chatClient.prompt()
    .system("다음 문서를 기반으로 답하세요:\n" + faq + policies + ...)
    .user(question)
    .call()
    .content();
```

Layer 1-2만 해도 수십 개 파일. Layer 3는 JSONL로 3,000건 이상입니다.

## 문제

모든 LLM에는 **컨텍스트 창(context window)** 제한이 있습니다.

| 모델 | 컨텍스트 창 | 100만 토큰 입력 비용 |
|------|------------|---------------------|
| gpt-4o-mini | 128K 토큰 | $0.10 |
| gpt-4.1 | 128K 토큰 | $2.00 |

현재 데이터 총합이 수백만 토큰입니다. 전부 넣으면:
1. 컨텍스트 창 초과 오류
2. 초과하지 않더라도 요청당 수천 원

그리고 실제로 "반품 기간" 질문에 상담 로그 3,000건이 필요할까요?

## 핵심 통찰

매번 **전체** 데이터를 보낼 필요가 없습니다.
질문과 **관련 있는 일부**만 찾아서 보내면 됩니다.

문제는: **어떻게 자동으로 관련 문서를 찾느냐**입니다.

키워드 매칭(`String.contains()`)은 동작하지 않습니다:
- "걍 환불해주세요 ㅠㅠ" → "refund" 키워드 없음
- "배송 언제 와요?" → "delivery period" 키워드 없음

**의미**로 찾아야 합니다.

## 다음에 조사해볼 것

"text embedding similarity search"
"semantic search vs keyword search"

## 이 문제가 Stage 2로 이어지는 방식

이 벽이 **M1 (벡터 DB & ETL)** 과 **M2 (검색 품질)** 의 존재 이유입니다.

지금 당장 완벽하게 해결할 필요는 없습니다.
일단 어떤 방식으로든 동작하게 만들고, 벽 리포트에 기록하세요.

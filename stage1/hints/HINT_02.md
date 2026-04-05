# 힌트 02: 포맷 혼재 — 데이터 읽기가 생각보다 복잡하다

데이터가 세 가지 포맷으로 나뉘어 있습니다.

## Layer 1: Markdown (`.md`)

```markdown
## Orders & Order Management

### How do I cancel an order?

You can cancel an order within 2 hours of placing it...
```

`Files.readString()`으로 읽히지만, 어디서 자를 것인가?
- 파일 단위? → 너무 큼
- 문단 단위? → Q&A가 분리됨
- `###` 기준? → Q&A 단위로 자를 수 있음

## Layer 2: Markdown + YAML Frontmatter (`.md`)

```markdown
---
title: Return & Refund Policy
version: v3
status: current
effective_date: 2024-04-01
supersedes: return-policy-v2.md
---

# Return & Refund Policy v3
...
```

Frontmatter(`---` 사이)에 메타데이터가 있습니다.
`deprecated/` 폴더에는 구버전 정책도 있습니다 — v1, v2가 현행 v3와 다른 내용을 담고 있습니다.
어떤 버전을 우선해야 할까요?

## Layer 3: JSONL (`.jsonl`)

한 줄 = 한 대화:

```json
{"conversation_id":"CHAT-2024-08-001","turns":[{"role":"customer","text":"어제 주문했는데 배송 언제 와요?"},{"role":"agent","text":"주문번호 알려주시면 확인해드릴게요."}],"primary_intent":"delivery_period","resolution":"resolved"}
```

Jackson으로 파싱:

```java
ObjectMapper mapper = new ObjectMapper();
List<ChatLog> logs = Files.lines(Path.of("2024-01.jsonl"))
    .map(line -> mapper.readValue(line, ChatLog.class))
    .toList();
```

그런데 상담 로그에서 무엇을 추출할 것인가?
- 전체 대화? → 노이즈가 많음
- 고객 질문만? → 문맥이 없음
- agent 답변만? → 정확하지 않을 수 있음 (상담사 실수 포함)

## 현실적인 접근

지금은 완벽한 파싱보다 **동작하는 것**이 먼저입니다.

Layer 1부터 시작하세요. FAQ 문서가 "정답의 앵커"입니다.
Layer 2, 3는 나중에 추가해도 됩니다.

다만 파싱하면서 발견한 문제 — 버전 충돌, 노이즈, 포맷 불일치 —
이것들을 **벽 리포트에 기록**하세요. 이게 M1의 학습 내용이 됩니다.

## Spring AI DocumentReader

Spring AI에는 문서 읽기를 도와주는 클래스가 있습니다:

```java
// 마크다운 읽기
Resource resource = new FileSystemResource("layer1_faq/orders.md");
TextReader reader = new TextReader(resource);
List<Document> docs = reader.get();
```

다만 청킹(분할)은 직접 해야 합니다.

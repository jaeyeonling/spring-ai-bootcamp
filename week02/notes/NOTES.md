# Week 02 — 청킹과 평가 구현 노트

> **미션**: Week 01 챗봇의 정확도가 ~40-50%. 청킹 전략 개선 + 프롬프트 엔지니어링 + 자동 평가로 70% 이상 달성.  
> **핵심 전환**: "잘 되는 것 같은데?" → 수치로 증명하기.

## 관련 개념

- 청킹 (Chunking)
- [프롬프트 엔지니어링 (Prompt Engineering)](CONCEPT_PROMPT_ENGINEERING.md)
- RAG (Retrieval-Augmented Generation)
- 임베딩 (Embedding)

---

## 구현 파일 구조

```
week02/src/main/java/com/jaeyeonling/week02/
├── ChunkingStrategy.java        — 청킹 전략 인터페이스 (Strategy 패턴)
├── MarkdownHeaderChunker.java   — 전략 1: ## 섹션 헤더 보존 분할
├── TokenWindowChunker.java      — 전략 2: 토큰 윈도우 + 오버랩
├── ChatService.java             — RAG 파이프라인 + 프롬프트 엔지니어링
├── EvaluationRunner.java        — RelevancyEvaluator 기반 자동 평가
└── Week02Application.java       — Bean 설정 (전략 교체 지점)
```

---

## 구현 흐름

```
[전략 교체]
  Week02Application에서 ChunkingStrategy Bean 등록
  → MarkdownHeaderChunker 또는 TokenWindowChunker 선택

[초기화]
  ChatService.ensureInitialized()
    FaqLoader.loadDocument() → FAQ 원문
    chunkingStrategy.split(content) → 청크 리스트
    InMemoryVectorStore.addAll(chunks) → 임베딩 + 저장

[질의]
  ChatService.answerQuestion(question)
    embed(question) → 벡터
    InMemoryVectorStore.search(vector, topK=3) → 관련 청크
    buildSystemPrompt(context) → Few-shot + CoT 프롬프트
    ChatClient.prompt().system(prompt).user(question).call()

[평가]
  EvaluationRunner.runEvaluation(testQuestions)
    for each question:
      answerQuestion(q) → actual answer
      RelevancyEvaluator.evaluate(q, expected, actual) → pass/fail
    → EvaluationReport { total, passed, failed, accuracy }
```

---

## 핵심 구현 포인트

### Strategy 패턴 — ChunkingStrategy 인터페이스

```java
public interface ChunkingStrategy {
    List<String> split(String content);
}
```

한 줄 인터페이스지만, 이것이 Week 02의 핵심 설계 결정이다. Week 01에서 `FaqLoader.splitIntoChunks()`에 하드코딩된 `"### "` 분할을 Strategy 패턴으로 추출함으로써:

- 전략 교체가 Bean 설정 한 줄로 가능
- 평가 시 동일한 파이프라인에서 전략만 바꿔가며 비교 가능
- `Week02Application`에서 `new MarkdownHeaderChunker()` ↔ `new TokenWindowChunker(300, 50)`만 교체

> 이 패턴은 Week 04의 ReRanker 전략 교체에서도 동일하게 재등장한다.

### MarkdownHeaderChunker — `##` 섹션 컨텍스트 보존

Week 01의 `"### "` 분할과의 핵심 차이: **상위 `##` 섹션 헤더를 각 청크에 prepend**.

```java
// Week 01: "### Under what conditions can I return a product?\n..."
// Week 02: "## Returns & Refunds\n\n### Under what conditions can I return a product?\n..."
```

왜 이게 중요한가:

```
FAQ 원문:
  ## Shipping & Delivery
    ### How much does shipping cost?
    → 청크: "### How much does shipping cost?\n..."

  ## Returns & Refunds
    ### How much does return shipping cost?
    → 청크: "### How much does return shipping cost?\n..."
```

"반품 배송비"를 물어보면, 두 청크 모두 "shipping cost"가 포함되어 있어 유사도가 비슷하게 나온다. 하지만 `## Returns & Refunds`가 prepend되어 있으면 임베딩 벡터가 "반품" 맥락으로 기울면서 정확한 청크가 1위가 된다.

구현 로직:

```java
// 라인 단위 순회하며 현재 ## 섹션 추적
if (line.startsWith("## ")) {
    currentSection = line;  // "## Returns & Refunds"
} else if (line.startsWith("### ")) {
    saveCurrentChunk();     // 이전 청크 저장
    // 새 청크: 섹션 헤더 + ### Q&A
    currentChunk = currentSection + "\n\n" + line;
}
```

### TokenWindowChunker — 오버랩으로 경계 맥락 보존

구조 기반이 아닌 **크기 기반** 분할. 단어 수를 토큰 프록시로 사용.

```java
// chunkSize=5, overlap=2일 때:
// Chunk 1: [A B C D E]
// Chunk 2:       [D E F G H]     ← D, E 공유
// Chunk 3:             [G H I J K]   ← G, H 공유

int step = chunkSize - overlap;  // 슬라이드 폭
```

**트레이드오프**:

| | MarkdownHeaderChunker | TokenWindowChunker |
|-|----------------------|-------------------|
| 청크 크기 | 불균일 (Q&A마다 다름) | 균일 (chunkSize 고정) |
| 구조 보존 | O (섹션+Q&A 단위) | X (의미 경계 무시) |
| 범용성 | 마크다운에만 적용 | 아무 텍스트나 가능 |
| 경계 맥락 | 없음 (완전 분리) | O (오버랩 영역 공유) |

결과적으로 FAQ처럼 구조가 명확한 문서에는 `MarkdownHeaderChunker`가 우수. 비정형 문서(매뉴얼, 정책서)에는 `TokenWindowChunker`가 안정적. → Week 03에서 `TokenTextSplitter`(Spring AI 제공)로 수렴하는 이유.

### 프롬프트 엔지니어링 — Few-shot + Chain-of-Thought

최종 프롬프트 (Experiment 6까지 반복한 결과물). 상세는 [프롬프트 엔지니어링 개념 노트](CONCEPT_PROMPT_ENGINEERING.md) 참조.

```
당신은 초록 코퍼레이션의 고객지원 도우미입니다.
아래 FAQ 컨텍스트만을 기반으로 질문에 답하세요.

## 답변 규칙
1. 컨텍스트에서 질문과 관련된 부분을 찾으세요.        ← CoT step 1
2. 관련 부분에서 구체적인 사실을 추출하세요.          ← CoT step 2
3. 추출한 사실을 바탕으로 명확하고 간결하게 답변하세요.← CoT step 3
4. 컨텍스트에 답이 없으면 "해당 내용은 FAQ에서 찾을 수 없습니다"
5. 질문과 동일한 언어로 답변하세요.                   ← cross-language

## 좋은 답변 예시                                     ← Few-shot
Q: How long does standard delivery take?
A: Standard delivery takes 3-5 business days...

Q: 반품 기간이 어떻게 되나요?
A: 배송 후 14일 이내에...

Q: What payment methods are accepted?
A: We accept credit cards (Visa, Mastercard, BC Card)...

## FAQ 컨텍스트
{context}
```

적용한 프롬프트 엔지니어링 기법:

| 기법 | 효과 | 프롬프트 내 위치 |
|------|------|-----------------|
| **Chain-of-Thought** | LLM이 "찾기 → 추출 → 답변" 단계를 거치도록 유도. 건너뛰기 방지 | 답변 규칙 1-3 |
| **Few-shot** | 기대하는 답변 형식과 구체성 수준을 예시로 시연 | 좋은 답변 예시 |
| **Cross-language 지시** | 한국어 질문에 한국어로 답하도록 명시 | 규칙 5 |
| **환각 방지** | 컨텍스트에 없으면 거부하도록 강제 | 규칙 4 |

> "Experiment 6"이라는 주석에서 알 수 있듯이, 프롬프트는 한 번에 완성되지 않았다. 베이스라인 → CoT만 → Few-shot만 → 둘 다 조합 → 예시 문구 튜닝 순서로 반복 실험이 필요했다.

### EvaluationRunner — LLM이 LLM을 평가

```java
// 핵심 루프
for (TestQuestion tq : questions) {
    String actual = chatService.answerQuestion(tq.question());

    EvaluationRequest request = new EvaluationRequest(
        tq.question(),
        List.of(new Document(tq.expectedAnswer())),  // context = expected answer
        actual
    );

    EvaluationResponse result = evaluator.evaluate(request);
    // result.isPass(), result.getScore(), result.getFeedback()
}
```

`RelevancyEvaluator`의 동작 방식:

1. 내부적으로 LLM을 호출하여 "주어진 context를 기반으로, actual answer가 question에 대해 관련 있는 답변인가?" 판단
2. `isPass()` → boolean, `getScore()` → 0.0~1.0, `getFeedback()` → 왜 실패했는지 설명

**주의할 점**:

- 평가 1건 = LLM 호출 1회 → 50문항 평가 = 50회 추가 API 호출. 비용이 든다.
- LLM이 LLM을 평가하는 구조이므로 100% 정확하진 않다. 하지만 수동 검수의 시간 비용 대비 합리적인 트레이드오프.
- `getFeedback()`을 로그에 남기면 "왜 틀렸는지"를 알 수 있어서 프롬프트/청킹 개선 방향을 잡을 수 있다.

---

## Spring AI API 키워드

| API | 설명 |
|-----|------|
| `ChunkingStrategy` | 직접 정의한 전략 인터페이스. Spring AI 공식 API가 아닌 도메인 인터페이스 |
| `TokenTextSplitter` | Spring AI 제공 청크 분할기. `DocumentTransformer` 구현체. 토큰 수 기반 분할 |
| `RelevancyEvaluator` | Spring AI 제공 평가기. LLM으로 답변의 관련성을 자동 판정 |
| `EvaluationRequest` | 평가 입력: (질문, 컨텍스트 문서 리스트, 실제 답변) |
| `EvaluationResponse` | 평가 결과: `isPass()`, `getScore()`, `getFeedback()` |

---

## 테스트 결과

- `ChunkingStrategyTest` — 단위 테스트 (API 키 불필요)
  - MarkdownHeaderChunker (8개):
    - [x] 빈 문서 → 빈 리스트
    - [x] 각 청크에 `###` 포함
    - [x] 정확한 청크 수 (>=5)
    - [x] `## 섹션` 헤더가 각 청크에 prepend됨
    - [x] 섹션 헤더가 섹션에 따라 변경됨
    - [x] `##` 헤더만 단독 청크로 분리되지 않음
    - [x] `##` 없이 `###`만 있어도 동작
    - [x] 비어있지 않은 청크 생성
  - TokenWindowChunker (6개):
    - [x] 비어있지 않은 청크 생성
    - [x] 청크 크기 제한 준수
    - [x] 큰 chunkSize → 적은 청크 수
    - [x] 오버랩 영역 공유 확인
    - [x] 빈 문서 → 빈 리스트
    - [x] 짧은 문서 → 단일 청크

- `EvaluationRunnerTest` — 통합 테스트 (OpenAI API 키 필요)
  - [x] 영어 50문항 기준선 평가 실행
  - [x] 한국어 50문항 기준선 평가 실행

---

## 배운 것 / 느낀 것

### "Experiment 6"까지 반복한 이유

프롬프트 엔지니어링은 이론으로 배우면 "Few-shot 넣고, CoT 넣으면 된다"로 끝난다. 하지만 실제로는:

1. **Experiment 1** (Week 01 프롬프트 그대로): 기준선 측정. 40-50%대.
2. **Experiment 2** (CoT만 추가): "관련 부분을 찾으세요" 지시. 소폭 개선.
3. **Experiment 3** (Few-shot만 추가): 예시 Q&A 추가. 영어 질문은 크게 개선, 한국어는 부진.
4. **Experiment 4** (CoT + Few-shot): 둘 다 조합. 개선 폭 확대.
5. **Experiment 5** (cross-language 지시 추가): "질문과 동일한 언어로 답변" 명시. 한국어 정확도 급상승.
6. **Experiment 6** (Few-shot에 한국어 예시 포함): 영어+한국어 예시를 섞음. 최종 채택.

> **학습자가 빠지기 쉬운 함정**: 프롬프트를 1-2번 바꿔보고 "별 차이 없네"라고 포기하기. 6번 반복해야 차이가 보이는 건, 각 변경의 효과가 작기 때문이 아니라 **측정 없이는 차이를 감지할 수 없기 때문**이다. EvaluationRunner가 있어야 비로소 "이번 변경이 +3% 개선인가 -2% 퇴보인가"를 판단할 수 있다.

### 청킹 전략이 정확도에 미치는 영향

같은 프롬프트, 같은 top-K에서 청킹 전략만 바꿨을 때:

| 전략 | 특징 | 예상 효과 |
|------|------|----------|
| Week 01 `"### "` split | 섹션 맥락 없음 | 비슷한 제목의 Q&A 혼동 |
| `MarkdownHeaderChunker` | `## 섹션` 헤더 보존 | 카테고리 구분 정확도 향상 |
| `TokenWindowChunker(300, 50)` | 크기 균일, 오버랩 | 긴 Q&A의 후반부 검색 개선 |

`MarkdownHeaderChunker`가 최종 채택된 이유: FAQ는 구조가 명확한 마크다운이므로, 구조를 활용하는 전략이 범용 전략보다 유리했다. 하지만 이것은 **FAQ라서** 그런 것이지, 비정형 문서에는 `TokenWindowChunker`가 더 안정적이다.

> **이 실험에서 체감한 것**: 청킹 전략은 "좋고 나쁨"이 아니라 "문서 구조와의 궁합"이다. Week 03에서 PDF, DOCX 같은 비정형 문서를 다루게 되면 `TokenTextSplitter`가 기본값이 되는 이유.

### `RelevancyEvaluator`의 한계와 가치

**가치**: 수동으로 50문항을 읽고 판단하면 30분. `RelevancyEvaluator`는 2-3분에 전체를 평가하고 실패 사유까지 제공한다. Experiment 1~6을 반복할 수 있었던 이유.

**한계**:

- LLM이 "관련 있다"고 판단하는 기준이 사람과 다를 수 있다. "14일"이라는 숫자가 빠진 답변을 "관련 있음"으로 판정하는 경우가 있었다.
- 평가 자체에 토큰을 소비한다. 50문항 x 평가 1회 = 약 $0.02 정도지만, Experiment를 6번 반복하면 누적된다.
- `expectedAnswer`를 context로 제공하는 방식이라, "기대 답변과 유사한가?"를 평가하는 것이지 "사실적으로 정확한가?"를 평가하는 것은 아니다.

> Week 05의 환각 탐지에서 이 한계를 보완하게 된다 — 답변이 컨텍스트에 "근거"가 있는지를 별도로 검증하는 방식.

### 테스트 데이터 설계: 영어 50문항 + 한국어 50문항

`test_questions.json`(영어)과 `test_questions_ko.json`(한국어)을 분리한 이유:

- **영어 → 영어**: 질문 언어와 FAQ 언어가 같으므로, 순수한 검색+생성 품질 측정
- **한국어 → 영어**: cross-language 임베딩의 효과 측정. 임베딩 모델이 다국어를 얼마나 잘 매핑하는지

**50문항의 커버리지**: 17개 FAQ 섹션에서 골고루 출제. Shipping, Returns, Payments, Accounts, Products, Support, Promotions, Subscriptions, Corporate/Bulk, Privacy, App/Tech, Gift Cards, Accessibility, Environment, Order Management, International, Marketplace 등.

> **학습자 포인트**: 테스트 질문이 한쪽 카테고리에 치우치면 "특정 섹션만 잘 되는 전략"이 높은 점수를 받을 수 있다. 골고루 분포된 테스트 셋이 전략 비교에 필수적.

### `Week02Application`의 Bean 설정 — 실험 전환점

```java
@Bean
public ChunkingStrategy chunkingStrategy() {
    // Experiment 6: MarkdownHeaderChunker + improved prompt (Few-shot + CoT)
    return new MarkdownHeaderChunker();
}
```

이 한 줄이 실험의 전환점 역할을 한다. `new TokenWindowChunker(300, 50)`으로 바꾸고 테스트를 돌리면 다른 전략의 결과가 나온다. 코드 수정 범위가 1줄이라는 것은 Strategy 패턴의 효과이고, `EvaluationRunner`와 결합하면 **청킹 전략 A/B 테스트**가 완성된다.

> **프로덕션에서의 확장**: 이 패턴은 나중에 `@ConditionalOnProperty`로 설정 파일에서 전략을 교체하거나, A/B 테스트 프레임워크와 연결할 수 있다.

### Week 01 모듈 의존성 — 재사용의 양면

```groovy
dependencies {
    implementation project(':shared')
    implementation project(':week01')  // FaqLoader, InMemoryVectorStore 재사용
}
```

`week01`의 `FaqLoader`(파일 읽기)와 `InMemoryVectorStore`(임베딩 저장/검색)를 재사용했다. 장점은 코드 중복 제거지만, 단점은 명확하다:

- `InMemoryVectorStore`는 앱 재시작 시 데이터 소실
- 초기화마다 OpenAI Embedding API 호출 → 비용 + 시간
- 청크 수가 늘어나면 검색 속도 O(n) 선형 증가

→ 이 한계가 Week 03(Qdrant)으로 넘어가는 직접적인 동기.

---

## 이 구현에서 느끼게 되는 한계 (Week 03+ 복선)

| 한계 | 언제 체감되는가 | 해결 주차 |
|------|----------------|-----------|
| InMemoryVectorStore 데이터 소실 | 앱 재시작 → 임베딩 재생성 비용+시간 | Week 03 (Qdrant) |
| 문서 형식이 마크다운뿐 | PDF, DOCX 업로드 불가 | Week 03 (TikaDocumentReader) |
| 검색 속도 O(n) | 청크 1000개 넘으면 체감 지연 | Week 03 (HNSW 인덱스) |
| top-K 중 노이즈 청크 | 관련은 있지만 최선이 아닌 청크가 섞임 | Week 04 (ReRanker) |
| 정확도 70%대에서 정체 | 프롬프트만으로는 한계 | Week 04 (쿼리 변환, 하이브리드 검색) |

---

## 다음 주차로 연결

Week 03에서 해결할 것:
- **영속성**: InMemoryVectorStore → Qdrant. 서버 재시작해도 임베딩 유지
- **다양한 문서 형식**: `TikaDocumentReader`로 PDF, DOCX, TXT 등 지원
- **ETL 파이프라인**: Reader → Transformer → Writer 표준 패턴
- **검색 성능**: 브루트포스 O(n) → HNSW ANN O(log n)

→ [Week 03 구현 노트](../../week03/notes/NOTES.md)

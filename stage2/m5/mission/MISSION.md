# M5 미션: 비용 최적화

## Stage 1에서 겪은 벽

벽 리포트에 이런 내용이 있을 것입니다:

> "테스트 100개 돌렸더니 $3 나왔다. 하루 1,000 요청이면 월 $900."
> "매 요청마다 context를 다 넣으니까 토큰이 너무 많다."
> "같은 질문이 반복되는데 매번 LLM을 호출한다."

---

## 미션

Stage 1에서 측정한 비용을 기준으로, **동일한 품질**을 유지하면서 운영 비용을 줄이세요.

얼마나 줄일 수 있는지는 직접 측정해서 확인합니다.

### 완료 기준

```
[ ] 비용 추적 대시보드: 요청별 토큰 사용량, 일/월 비용 집계
[ ] Ollama 로컬 모델 연결 (gpt-4o-mini 대체)
[ ] 모델 라우팅: 질문 난이도에 따라 로컬/클라우드 모델 분배
[ ] 시맨틱 캐싱: 유사 질문 재사용으로 LLM 호출 절감
[ ] 비용 절감 전후 비교 수치 기록
```

---

## 접근 전략

### 1단계: 현재 비용 파악

절감하기 전에 어디서 비용이 나오는지 먼저 파악하세요.

```java
@Component
public class CostTracker {

    private final MeterRegistry registry;

    public void record(String model, Usage usage) {
        registry.counter("llm.tokens.prompt",
            "model", model).increment(usage.getPromptTokens());
        registry.counter("llm.tokens.completion",
            "model", model).increment(usage.getCompletionTokens());
    }
}
```

### 2단계: 시맨틱 캐싱 (즉각적 효과)

같은 질문이 반복될 때 LLM 호출 없이 캐시된 답변을 반환합니다.

"배송 기간이 얼마나 되나요?" 와 "배송 얼마나 걸려요?" 는
키워드가 다르지만 의미가 같습니다. 벡터 유사도로 캐시 히트를 판단합니다.

```
[질문] → [임베딩] → [캐시 검색] → 유사도 > 0.95이면 캐시 반환
                                  → 유사도 < 0.95이면 LLM 호출 후 캐시 저장
```

### 3단계: 모델 라우팅

모든 질문에 gpt-4o가 필요하지 않습니다.

```java
public String route(String question) {
    // 단순 FAQ 질문: 로컬 모델로 처리
    if (isSimpleFactualQuestion(question)) {
        return "ollama/llama3.2";
    }
    // 복잡한 추론, 교차 소스: 클라우드 모델
    return "openai/gpt-4o-mini";
}
```

라우팅 기준을 어떻게 설계할지가 핵심 설계 포인트입니다.
단순하게는 질문 길이, 복잡하게는 LLM이 직접 복잡도를 판단합니다.

### 4단계: Ollama 설정

```bash
# Ollama 설치 후
ollama pull llama3.2
ollama pull nomic-embed-text  # 임베딩용 로컬 모델
```

```yaml
spring:
  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        model: llama3.2
```

**주의**: Ollama CPU 처리량은 5-15 tok/s 수준입니다.
빠른 응답이 필요한 경우에는 클라우드 모델을 유지해야 합니다.

---

## 비용 절감 기록

각 전략을 적용하기 전과 후의 수치를 직접 측정하세요.

| 전략 | 적용 전 비용 | 적용 후 비용 | 절감폭 | 품질 변화 |
|------|------------|------------|--------|---------|
| 시맨틱 캐싱 | ?원 | ?원 | ?% | ? |
| 모델 라우팅 | ?원 | ?원 | ?% | ? |
| 컨텍스트 최적화 | ?원 | ?원 | ?% | ? |

절감 효과와 품질 저하 사이의 트레이드오프를 직접 측정하고, 어떤 전략이 이 데이터셋에서 가장 효과적이었는지 설명할 수 있어야 합니다.

---

## 힌트

| 힌트 | 이럴 때 열어보세요 |
|------|-------------------|
| `HINT_01.md` | Micrometer로 토큰/비용 추적이 막힐 때 |
| `HINT_02.md` | Ollama 설치 및 Spring AI 연결이 막힐 때 |
| `HINT_03.md` | 시맨틱 캐싱 구현이 막힐 때 |

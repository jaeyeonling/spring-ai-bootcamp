# 힌트 02: Ollama 로컬 모델 + 모델 라우팅

## Ollama 설치 및 모델 다운로드

```bash
# Ollama 설치 (macOS)
brew install ollama

# 모델 다운로드
ollama pull llama3.2         # 채팅용 (3B 파라미터, ~2GB)
ollama pull nomic-embed-text # 임베딩용 (로컬 임베딩 원할 때)

# 실행 확인
ollama run llama3.2 "안녕하세요"
```

## Spring AI Ollama 설정

```yaml
spring:
  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        model: llama3.2
        options:
          temperature: 0.1
          num-ctx: 4096
```

```java
// Ollama ChatModel 주입
@Qualifier("ollamaChatModel")
private final ChatModel ollamaModel;

// OpenAI ChatModel 주입
@Qualifier("openAiChatModel")
private final ChatModel openAiModel;
```

## 모델 라우팅 전략

```java
@Service
public class ModelRouter {

    public String route(String question) {
        // 단순 분류: 질문 유형으로 모델 선택
        if (isSimpleFactual(question)) {
            return "ollama";   // 로컬 — 기본 FAQ 질문
        }
        if (requiresReasoning(question)) {
            return "openai";   // 클라우드 — 교차 소스, 복잡한 추론
        }
        return "ollama";  // 기본값: 로컬
    }

    private boolean isSimpleFactual(String question) {
        // 단순 기준: 질문이 짧고, 한 가지만 묻는 경우
        return question.length() < 50 && !question.contains("그리고") && !question.contains("또한");
    }

    private boolean requiresReasoning(String question) {
        // 복잡한 기준: 여러 조건, 교차 주제
        return question.contains("경우") || question.contains("만약") || question.contains("차이");
    }
}
```

더 정교한 라우팅: LLM에게 직접 복잡도를 판단하게 합니다:

```java
public String routeWithLlm(String question) {
    // 작은 모델(Ollama)로 복잡도 판단 — 비용 거의 없음
    String complexity = ollamaClient.prompt()
        .user("다음 질문의 복잡도를 판단하세요 (simple/complex): " + question)
        .call()
        .content()
        .trim()
        .toLowerCase();

    return complexity.contains("complex") ? "openai" : "ollama";
}
```

## Ollama 성능 현실

| 환경 | 처리량 | 지연시간 |
|------|--------|---------|
| CPU (M2 Mac) | 15-25 tok/s | 5-15초 |
| CPU (일반 PC) | 5-15 tok/s | 10-30초 |
| GPU (RTX 3080) | 50-100 tok/s | 1-3초 |

CPU 환경에서는 Ollama가 느립니다.
빠른 응답이 필요한 케이스(고객 대기 중)는 OpenAI로 라우팅하세요.

## 라우팅 효과 측정

```
전략           | 정확도 | 비용/100req | 평균 응답시간
────────────────────────────────────────────────
전체 OpenAI    | 54%   | $0.035      | 1.2초
전체 Ollama    | ?%    | ~$0         | ?초
라우팅 (80/20) | ?%    | ?$          | ?초
```

품질 저하 없이 비용 절감이 가능한 최적 비율을 찾으세요.

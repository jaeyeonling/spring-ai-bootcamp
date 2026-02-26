# 힌트 01: Spring AI와 Ollama 통합

## Ollama 실행하기

가장 빠른 시작 방법:

```bash
# Docker로 Ollama 실행 (llama3.2는 약 4GB 다운로드)
docker run -d -v ollama:/root/.ollama -p 11434:11434 --name ollama ollama/ollama

# 모델 가져오기
docker exec -it ollama ollama pull llama3.2

# 작동 확인
curl http://localhost:11434/api/chat -d '{
  "model": "llama3.2",
  "messages": [{"role": "user", "content": "안녕하세요"}],
  "stream": false
}'
```

또는 macOS에 네이티브 설치:
```bash
brew install ollama
ollama serve &
ollama pull llama3.2
```

## Spring AI Ollama 스타터

의존성 추가:

```xml
<!-- Maven -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-ollama</artifactId>
</dependency>
```

```groovy
// Gradle
implementation 'org.springframework.ai:spring-ai-starter-model-ollama'
```

## 프로파일로 OpenAI와 Ollama 간 전환

핵심 통찰: Spring AI의 `ChatModel` 인터페이스는 공급자에 독립적입니다.
`OpenAiChatModel`과 `OllamaChatModel` 모두 이것을 구현합니다. 특정 구현이 아닌
`ChatModel`을 대상으로 코드를 작성하면, 전환은 프로파일 변경만으로 됩니다.

### application.yml (기본 — OpenAI)

```yaml
spring:
  application:
    name: week06-local-models
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat:
        options:
          model: gpt-4o-mini
          temperature: 0.1
      embedding:
        options:
          model: text-embedding-3-small
```

### application-ollama.yml

```yaml
spring:
  ai:
    openai:
      # Ollama 사용 시 OpenAI API 키가 없어도 시작되도록 기본값 설정
      # 참고: spring.ai.openai.enabled 같은 속성은 Spring AI 1.1.2에 존재하지 않습니다.
      api-key: ${OPENAI_API_KEY:unused}
    ollama:
      base-url: http://localhost:11434
      chat:
        options:
          model: llama3.2
          temperature: 0.1
          num-predict: 512
      embedding:
        options:
          model: nomic-embed-text
```

### ModelRoutingService 패턴

```java
@Service
public class ModelRoutingService {

    private final ChatModel chatModel; // Spring이 프로파일 기반으로 올바른 것을 주입

    public ModelRoutingService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public String chat(String userMessage) {
        return ChatClient.create(chatModel)
            .prompt()
            .user(userMessage)
            .call()
            .content();
    }
}
```

`--spring.profiles.active=ollama`로 실행하면 Spring Boot가 `application-ollama.yml`을
활성화하여 `OllamaChatModel`을 `ChatModel` 빈으로 설정합니다.
그 없이 실행하면 `OpenAiChatModel`이 사용됩니다.

코드에 `if/else` 없습니다. 그냥 Spring 프로파일입니다.

## Ollama의 임베딩 모델

Ollama는 임베딩 모델도 제공합니다:

```bash
ollama pull nomic-embed-text
```

즉, OpenAI API 없이 전체 RAG 파이프라인을 로컬에서 실행할 수 있습니다.

## 일반적인 함정

1. **두 스타터가 클래스패스에 있는 경우**: `spring-ai-starter-model-openai`와
   `spring-ai-starter-model-ollama`가 모두 있으면, Spring이 두 `ChatModel` 빈을
   모두 생성하려 합니다. 충돌을 피하기 위해 `@ConditionalOnProperty`를 사용하거나
   자동 설정을 제외하세요.

2. **모델 크기 vs. RAM**: `llama3.2` (3B 파라미터)는 ~4GB RAM이 필요합니다.
   `llama3.1:70b`는 ~40GB가 필요합니다. 작은 것부터 시작하세요.

3. **첫 번째 요청이 느림**: Ollama는 첫 번째 요청 시 모델을 메모리에 로드합니다.
   이후 요청은 빠릅니다. 헬스 체크에 워밍업 요청을 사용하세요.

# 힌트 03: 모두 합치기 — 첫 번째 RAG 파이프라인

지금 만들고 있는 것에는 이름이 있습니다: **RAG (검색 증강 생성, Retrieval-Augmented Generation)**.

```
사용자 질문
    |
    v
[1. 질문 임베딩]
    |
    v
[2. 유사도로 청크 검색]  <-- "검색(Retrieval)"
    |
    v
[3. 찾은 청크를 컨텍스트로 프롬프트 구성]  <-- "증강(Augmented)"
    |
    v
[4. LLM에 전송하여 답변 생성]  <-- "생성(Generation)"
    |
    v
답변
```

## Spring AI에서의 전체 흐름

### 1단계: 시작 시 FAQ 로드 및 분할

```java
@Component
public class FaqLoader {

    public List<String> loadAndSplit(String filePath) {
        String content = Files.readString(Path.of(filePath));

        // 간단한 전략: "###"으로 분할 (각 Q&A 섹션은 ###으로 시작)
        return Arrays.stream(content.split("(?=### )"))
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .toList();
    }
}
```

### 2단계: 모든 청크를 임베딩하고 메모리에 저장

```java
@Component
public class InMemoryVectorStore {

    private final List<EmbeddedChunk> chunks = new ArrayList<>();

    public record EmbeddedChunk(String text, float[] vector) {}

    public void addAll(List<String> texts, EmbeddingModel embeddingModel) {
        for (String text : texts) {
            float[] vector = embeddingModel.embed(text);
            chunks.add(new EmbeddedChunk(text, vector));
        }
    }

    public List<String> search(float[] queryVector, int topK) {
        // 코사인 유사도로 정렬하여 상위 K개 텍스트 반환
    }
}
```

### 3단계: 증강된 프롬프트 구성

```java
String context = String.join("\n\n", relevantChunks);

String answer = chatClient.prompt()
    .system("""
        당신은 초록 코퍼레이션의 고객지원 담당자입니다.
        아래의 컨텍스트에 기반해서만 질문에 답하세요.
        컨텍스트에 답이 없으면 "해당 정보를 찾을 수 없습니다."라고 말하세요.

        컨텍스트:
        %s
        """.formatted(context))
    .user(question)
    .call()
    .content();
```

### 4단계: 토큰 사용량 추적

```java
ChatResponse response = chatClient.prompt()
    .system(systemPrompt)
    .user(question)
    .call()
    .chatResponse();

String answer = response.getResult().getOutput().getText();
Usage usage = response.getMetadata().getUsage();
// usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens()
```

## 방금 만든 것

축하합니다 — RAG 프레임워크 없이 처음부터 RAG 시스템을 만들었습니다.

이것은 의도적입니다. 3주차에는 이 방식의 한계
(느린 검색, 재시작 시 데이터 손실)에 부딪히고 실제 벡터 데이터베이스로 업그레이드합니다.
하지만 이제 **왜** 그 도구들이 존재하는지 이해하게 되었습니다.

## 백엔드 개발자를 위한 비유

| RAG 개념 | 백엔드 동등 개념 |
|-------------|-------------------|
| 임베딩 | 데이터베이스 인덱스 (검색을 빠르게) |
| 벡터 유사도 | WHERE 절 + ORDER BY relevance |
| 청크 | 테이블의 행 |
| 인메모리 저장소 | HashMap 캐시 |
| 컨텍스트 창 | 요청 본문 크기 제한 |

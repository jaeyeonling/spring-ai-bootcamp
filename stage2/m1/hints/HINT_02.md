# 힌트 02: ETL 파이프라인 구조

Qdrant 연결은 됐습니다. 이제 3,100건을 어떻게 적재할 것인가.

## ETL 패턴

```
[Extract] 파일 읽기  →  [Transform] 청킹 + 메타데이터  →  [Load] Qdrant 저장
```

각 단계를 인터페이스로 분리하면 레이어별로 다른 전략을 조합할 수 있습니다:

```java
public interface DocumentExtractor {
    List<RawDocument> extract(Path sourcePath);
}

public interface DocumentTransformer {
    List<Document> transform(RawDocument raw);
}

public class IngestionService {

    public void ingest(Path dataDir) {
        // Layer 1: FAQ (마크다운)
        ingestLayer(dataDir.resolve("layer1_faq"),
            new MarkdownExtractor(),
            new HeaderBasedChunker("###"));

        // Layer 2: 정책 (YAML frontmatter + 마크다운)
        ingestLayer(dataDir.resolve("layer2_policies/current"),
            new FrontmatterExtractor(),
            new SectionChunker());

        // Layer 3: 상담 로그 (JSONL)
        ingestLayer(dataDir.resolve("layer3_chatlogs"),
            new JsonlExtractor(),
            new ConversationChunker());
    }
}
```

## Layer 1: 마크다운 FAQ 청킹

```java
public List<Document> chunkByHeader(String content, String sourceFile) {
    return Arrays.stream(content.split("(?=### )"))
        .map(String::trim)
        .filter(s -> !s.isBlank() && s.startsWith("###"))
        .map(chunk -> new Document(
            chunk,
            Map.of(
                "layer", "faq",
                "source_file", sourceFile,
                "category", extractCategory(sourceFile)  // orders, shipping, ...
            )
        ))
        .toList();
}
```

## Layer 2: YAML Frontmatter 파싱

```java
public Map<String, String> parseFrontmatter(String content) {
    if (!content.startsWith("---")) return Map.of();

    int end = content.indexOf("---", 3);
    String yaml = content.substring(3, end).trim();

    // 단순 파싱 (라이브러리 없이)
    return Arrays.stream(yaml.split("\n"))
        .map(line -> line.split(": ", 2))
        .filter(parts -> parts.length == 2)
        .collect(Collectors.toMap(
            parts -> parts[0].trim(),
            parts -> parts[1].trim()
        ));
}

// 사용 예
Map<String, String> meta = parseFrontmatter(content);
String version = meta.getOrDefault("version", "unknown");
String status = meta.getOrDefault("status", "unknown");
```

## Layer 3: JSONL 파싱

```java
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatLog(
    String conversation_id,
    List<Turn> turns,
    String resolution,
    String primary_intent,
    String agent_accuracy
) {
    public record Turn(String role, String text) {}

    // 상담 요약으로 변환 (청킹 단위)
    public String toSearchableText() {
        String customerQ = turns.stream()
            .filter(t -> t.role().equals("customer"))
            .map(Turn::text)
            .collect(Collectors.joining(" "));
        String agentA = turns.stream()
            .filter(t -> t.role().equals("agent"))
            .map(Turn::text)
            .collect(Collectors.joining(" "));
        return "고객: " + customerQ + "\n상담사: " + agentA;
    }
}
```

## Qdrant에 적재

```java
// Spring AI VectorStore 인터페이스 — Qdrant 구현체 자동 주입
private final VectorStore vectorStore;

public void load(List<Document> documents) {
    // 배치로 나눠서 적재 (API rate limit 고려)
    Lists.partition(documents, 100).forEach(batch -> {
        vectorStore.add(batch);
        log.info("적재 완료: {}건", batch.size());
    });
}
```

## ETL 실행 시점

서버 시작 시마다 재적재하면 30분이 걸립니다.
최초 1회만 실행하고, 이미 적재된 경우 건너뛰세요:

```java
@PostConstruct
public void initIfEmpty() {
    long count = vectorStore.similaritySearch(
        SearchRequest.query("test").withTopK(1)
    ).size();

    if (count == 0) {
        log.info("Qdrant가 비어있음 — ETL 시작");
        ingestionService.ingest(dataDir);
    } else {
        log.info("Qdrant 데이터 존재 — ETL 건너뜀");
    }
}
```

# 힌트 02: Spring AI ETL 파이프라인 (Reader -> Transformer -> Writer)

1주차에는 직접 파일 리더, 분할기, 임베딩 루프를 작성했습니다.
Spring AI에는 이를 위한 내장 패턴이 있습니다: **ETL 파이프라인**.

## 패턴

```
[Reader]  -->  [Transformer]  -->  [Writer]
   |                |                  |
   v                v                  v
PDF 읽어서      토큰 크기의          VectorStore에
Document로     청크로 분할           저장
```

각 단계는 `Document` 객체로 작동합니다 — Spring AI의 텍스트 + 메타데이터 표준 단위.

## 1단계: Reader — TikaDocumentReader

Apache Tika는 PDF, DOCX, PPTX, HTML, TXT, 그 외 1,000가지 이상의 포맷을 읽을 수 있습니다.
Spring AI는 이것을 `TikaDocumentReader`로 래핑합니다:

```java
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.Resource;

// 파일 업로드 (MultipartFile)에서
Resource resource = new ByteArrayResource(file.getBytes()) {
    @Override
    public String getFilename() {
        return file.getOriginalFilename();
    }
};

TikaDocumentReader reader = new TikaDocumentReader(resource);
List<Document> documents = reader.get();
// 각 Document에는 content(String), metadata(Map), id(UUID)가 있습니다
```

리더는 원시 텍스트를 추출합니다. 100페이지 PDF가 하나의 큰 `Document`가 됩니다.
이것을 분할해야 합니다.

## 2단계: Transformer — TokenTextSplitter

LLM에는 컨텍스트 창이 있습니다. 100페이지 문자열을 임베딩할 수 없습니다.
`TokenTextSplitter`는 토큰 수를 기반으로 텍스트를 청크로 분할합니다:

```java
import org.springframework.ai.transformer.splitter.TokenTextSplitter;

TokenTextSplitter splitter = new TokenTextSplitter();
// 기본값: 청크당 800 토큰, 350 토큰 오버랩
// 오버랩은 문장 중간에 잘리지 않도록 합니다

List<Document> chunks = splitter.apply(documents);
// 50,000 토큰 1개 문서 -> ~800 토큰의 ~70개 청크
```

커스터마이즈:

```java
TokenTextSplitter splitter = new TokenTextSplitter(
    800,    // defaultChunkSize
    350,    // minChunkSizeChars
    200,    // minChunkLengthToEmbed
    10000,  // maxNumChunks
    true    // keepSeparator
);
```

## 3단계: Writer — VectorStore.add()

청크를 Qdrant(또는 VectorStore 구현체)에 저장:

```java
vectorStore.add(chunks);
```

이 단 한 줄이:
1. 각 청크 텍스트에 `EmbeddingModel.embed()` 호출
2. 벡터 + 메타데이터를 Qdrant로 전송
3. Qdrant가 ANN 검색을 위해 인덱싱

## 완전한 인제스트 코드

하나의 메서드에 전체 ETL 파이프라인:

```java
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public int ingest(MultipartFile file) throws IOException {
    // 1. 읽기
    Resource resource = new ByteArrayResource(file.getBytes()) {
        @Override
        public String getFilename() {
            return file.getOriginalFilename();
        }
    };
    List<Document> rawDocuments = new TikaDocumentReader(resource).get();

    // 2. 변환 — 청크로 분할
    TokenTextSplitter splitter = new TokenTextSplitter();
    List<Document> chunks = splitter.apply(rawDocuments);

    // 3. 소스 메타데이터 추가 (파일 추적용)
    chunks.forEach(chunk ->
        chunk.getMetadata().put("source", file.getOriginalFilename())
    );

    // 4. 쓰기 — 임베딩 + Qdrant에 저장
    vectorStore.add(chunks);

    return chunks.size();
}
```

## 메타데이터: 왜 중요한가

각 `Document`에는 `Map<String, Object> metadata`가 있습니다. 이것으로 추적합니다:
- 청크가 어느 파일에서 왔는지
- 페이지 번호
- 인제스트 타임스탬프

나중에 검색할 때 결과에 이 메타데이터가 포함됩니다 — 응답에
"답변 출처: product-manual.pdf, 12페이지"를 표시할 수 있습니다.

## 다음 할 일

인제스트 완료. 이제 **쿼리** 경로를 어떻게 연결할까요?
1주차처럼 수동으로 (검색 -> 프롬프트 구성 -> LLM 호출) 할 수 있습니다.
하지만 Spring AI에는 더 나은 방법이 있습니다: VectorStore + 시스템 프롬프트 패턴.

힌트 03을 참고하세요.

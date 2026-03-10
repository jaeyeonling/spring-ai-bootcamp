package com.jaeyeonling.week04;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 앱 시작 시 FAQ 문서를 Qdrant에 적재하는 ETL 파이프라인.
 *
 * Week 03의 IngestionService와 동일한 구조:
 *   1. 읽기  — TikaDocumentReader로 faq.md 읽기
 *   2. 변환  — TokenTextSplitter로 청크 분할
 *   3. 쓰기  — VectorStore.add()로 Qdrant에 저장
 *
 * 이미 데이터가 있으면 재적재하지 않는다 (idempotent).
 */
@Component
public class FaqIngestionService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FaqIngestionService.class);

    private final VectorStore vectorStore;

    @Value("${data.file-path}")
    private String faqFilePath;

    public FaqIngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public void run(ApplicationArguments args) {
        // 이미 데이터가 있으면 재적재 생략
        List<Document> existing = vectorStore.similaritySearch(
                SearchRequest.builder().query("반품").topK(1).build());
        if (!existing.isEmpty()) {
            log.info("[ETL] 컬렉션에 데이터가 이미 있습니다. 적재를 생략합니다.");
            return;
        }

        log.info("[ETL] FAQ 적재 시작: {}", faqFilePath);

        // 1단계 — TikaDocumentReader로 faq.md 읽기
        List<Document> rawDocuments = new TikaDocumentReader(
                new FileSystemResource(faqFilePath)).get();

        // 2단계 — TokenTextSplitter로 청크 분할
        List<Document> chunks = new TokenTextSplitter().apply(rawDocuments);
        log.info("[ETL] {} 개 청크 생성", chunks.size());

        // 3단계 — Qdrant에 저장 (임베딩은 VectorStore 내부에서 처리)
        vectorStore.add(chunks);
        log.info("[ETL] 적재 완료: {} 개 청크", chunks.size());
    }
}

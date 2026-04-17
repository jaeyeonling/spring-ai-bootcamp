package com.jaeyeonling.stage2.m7;

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
 * 앱 시작 시 FAQ 문서를 Qdrant(week10-faq)에 적재하는 ETL 파이프라인.
 * 이미 데이터가 있으면 재적재하지 않는다 (idempotent).
 */
@Component
public class FaqIngestionService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FaqIngestionService.class);

    private final VectorStore vectorStore;

    @Value("${faq.file-path}")
    private String faqFilePath;

    public FaqIngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Document> existing = vectorStore.similaritySearch(
                SearchRequest.builder().query("반품").topK(1).build());
        if (!existing.isEmpty()) {
            log.info("[ETL] 컬렉션에 데이터가 이미 있습니다. 적재를 생략합니다.");
            return;
        }

        log.info("[ETL] FAQ 적재 시작: {}", faqFilePath);

        List<Document> rawDocuments = new TikaDocumentReader(
                new FileSystemResource(faqFilePath)).get();

        // Week 02 교훈: FAQ는 Q&A 단위 청킹이 효과적.
        // defaultChunkSize=400(기본 800보다 작게), minChunkSizeChars=100, overlap 허용
        // → ReRanker 사용 시 청크가 작을수록 관련성 판정 정밀도 향상
        TokenTextSplitter splitter = new TokenTextSplitter(400, 100, 5, 10000, true);
        List<Document> chunks = splitter.apply(rawDocuments);
        log.info("[ETL] {} 개 청크 생성", chunks.size());

        vectorStore.add(chunks);
        log.info("[ETL] 적재 완료: {} 개 청크", chunks.size());
    }
}

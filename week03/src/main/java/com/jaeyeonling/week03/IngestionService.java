package com.jaeyeonling.week03;

/**
 * ETL 파이프라인을 통한 문서 인제스트를 처리하는 서비스.
 *
 * 파이프라인:
 *   1. 읽기 — TikaDocumentReader가 PDF/DOCX/TXT 등에서 텍스트를 추출
 *   2. 변환 — TokenTextSplitter가 텍스트를 임베딩 가능한 청크로 분할
 *   3. 쓰기 — VectorStore.add()가 청크를 임베딩하고 Qdrant에 저장
 *
 * 1주차의 수동 파일 읽기 + 분할을 대체합니다.
 */

import java.io.IOException;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class IngestionService {

    private final VectorStore vectorStore;

    public IngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * ETL 파이프라인을 통해 파일을 인제스트합니다.
     *
     * @param file 업로드된 파일 (PDF, DOCX, TXT 등)
     * @return 생성되어 저장된 청크 수
     */
    public int ingest(MultipartFile file) throws IOException {
        // 1단계 — TikaDocumentReader를 사용해 파일 읽기
        Resource resource = new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename();
            }
        };
        List<Document> rawDocuments = new TikaDocumentReader(resource).get();

        // 2단계 — TokenTextSplitter를 사용해 청크로 분할
        TokenTextSplitter splitter = new TokenTextSplitter();
        List<Document> chunks = splitter.apply(rawDocuments);

        // 3단계 — 각 청크에 메타데이터 추가
        for (Document chunk : chunks) {
            chunk.getMetadata().put("source", file.getOriginalFilename());
        }

        // 4단계 — VectorStore를 통해 Qdrant에 저장
        vectorStore.add(chunks);

        return chunks.size();
    }
}

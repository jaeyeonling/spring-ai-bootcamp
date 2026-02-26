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
        // TODO: 1단계 — TikaDocumentReader를 사용해 파일 읽기
        //   MultipartFile 바이트에서 Resource를 생성하세요.
        //   ByteArrayResource를 사용하고 원본 파일명을 보존하기 위해 getFilename()을 오버라이드하세요.
        //   그런 다음: new TikaDocumentReader(resource).get()

        // TODO: 2단계 — TokenTextSplitter를 사용해 청크로 분할
        //   TokenTextSplitter 생성 (기본값 사용 가능: 청크당 ~800 토큰).
        //   원시 문서에 적용: splitter.apply(rawDocuments)

        // TODO: 3단계 — 각 청크에 메타데이터 추가
        //   결과를 역추적할 수 있도록 "source"를 원본 파일명으로 설정하세요.
        //   chunk.getMetadata().put("source", file.getOriginalFilename())

        // TODO: 4단계 — VectorStore를 통해 Qdrant에 저장
        //   vectorStore.add(chunks) — 임베딩과 저장을 한 번에 처리합니다.

        // TODO: 생성된 청크 수 반환

        throw new UnsupportedOperationException("구현하세요 — hints/HINT_02.md 참고");
    }
}

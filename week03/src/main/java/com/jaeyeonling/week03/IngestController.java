package com.jaeyeonling.week03;

/**
 * 문서 인제스트를 위한 REST 컨트롤러.
 *
 * 파일 업로드(PDF, DOCX, TXT 등)를 받아 ETL 파이프라인
 * (읽기 -> 분할 -> 임베딩 -> 저장)을 통해 처리하고
 * 생성된 청크 수를 반환합니다.
 */

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class IngestController {

    private final IngestionService ingestionService;

    public IngestController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping(value = "/ingest", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<IngestResponse> ingest(@RequestParam("file") MultipartFile file) {
        // TODO: 파일 검증 (비어있으면 안 됨, 지원 형식 확인)
        // TODO: ingestionService.ingest(file)을 호출해 ETL 파이프라인 실행
        // TODO: 파일명과 청크 수 반환
        // TODO: 에러를 우아하게 처리 (스택 트레이스가 아닌 500 메시지 반환)

        throw new UnsupportedOperationException("구현하세요 — mission/MISSION.md 참고");
    }

    public record IngestResponse(
        String filename,
        int chunks,
        String message
    ) {}
}

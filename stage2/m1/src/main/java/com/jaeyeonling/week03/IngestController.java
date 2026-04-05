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
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            int chunks = ingestionService.ingest(file);
            return ResponseEntity.ok(new IngestResponse(
                    file.getOriginalFilename(),
                    chunks,
                    "성공적으로 인제스트되었습니다"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new IngestResponse(
                    file.getOriginalFilename(),
                    0,
                    "인제스트 실패: " + e.getMessage()
            ));
        }
    }

    public record IngestResponse(
        String filename,
        int chunks,
        String message
    ) {}
}

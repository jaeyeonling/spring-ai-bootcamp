package com.jaeyeonling.stage2.m7;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * ETL 데이터 초기화 — 재시작 시 재적재 방지 전략.
 *
 * Docker 볼륨에 Qdrant 데이터가 영속화되므로,
 * 이미 데이터가 존재하면 재적재를 건너뛰어야 합니다.
 *
 * 학습 포인트:
 * - @PostConstruct로 앱 시작 시 자동 실행
 * - force-reingest 플래그로 강제 재적재 지원
 * - Qdrant 컬렉션 상태 확인 후 조건부 적재
 */
@Component
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Value("${app.etl.force-reingest:false}")
    private boolean forceReingest;

    @PostConstruct
    public void init() {
        log.info("=== ETL 초기화 시작 (forceReingest={}) ===", forceReingest);

        if (forceReingest || isVectorStoreEmpty()) {
            log.info("[ETL] 데이터 적재 시작...");
            ingestData();
            log.info("[ETL] 데이터 적재 완료");
        } else {
            log.info("[ETL] 기존 데이터 존재 — 적재 건너뜀");
        }
    }

    /**
     * TODO: Qdrant 컬렉션이 비어있는지 확인하는 로직을 구현하세요.
     *
     * 힌트:
     * - VectorStore를 주입받아 similaritySearch로 빈 컬렉션 여부를 판단하거나
     * - Qdrant REST API (GET /collections/{name}/points/count)를 직접 호출
     *
     * 현재는 false를 반환하여 적재를 건너뜁니다.
     * 실제 구현으로 교체하세요.
     */
    private boolean isVectorStoreEmpty() {
        // TODO: 실제 Qdrant 컬렉션 확인 로직으로 교체하세요
        log.warn("[ETL] isVectorStoreEmpty() 미구현 — 기본값 false (적재 건너뜀)");
        return false;
    }

    /**
     * TODO: FAQ 문서를 읽어 벡터 스토어에 적재하는 ETL 로직을 구현하세요.
     *
     * 힌트:
     * - TikaDocumentReader 또는 TextReader로 문서 읽기
     * - TokenTextSplitter로 청크 분할
     * - VectorStore.add()로 적재
     *
     * 현재는 no-op입니다. 실제 ETL 로직으로 교체하세요.
     */
    private void ingestData() {
        // TODO: 실제 ETL 로직으로 교체하세요
        log.warn("[ETL] ingestData() 미구현 — ETL 로직을 구현하세요");
    }
}

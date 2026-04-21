package com.jaeyeonling.shared;

/**
 * 모든 주차 모듈에서 공유하는 상수 모음.
 *
 * 매주 코드에서 참조하는 공통 값들을 중복과 불일치를 피하기 위해
 * 여기에 중앙 집중화했습니다.
 */
public final class FaqConstants {

    private FaqConstants() {
        // 유틸리티 클래스 — 인스턴스화 불가
    }

    // ── 파일 경로 ─────────────────────────────────────────────────────

    /** 각 주차 프로젝트 루트를 기준으로 FAQ 문서의 기본 경로. */
    public static final String DEFAULT_FAQ_PATH = "../data/faq.md";

    /** 테스트 질문 파일의 기본 경로. */
    public static final String DEFAULT_TEST_QUESTIONS_PATH = "../data/test_questions.json";

    // ── 벡터 저장소 ───────────────────────────────────────────────────

    /** Qdrant 벡터 저장소의 기본 컬렉션 이름. */
    public static final String DEFAULT_COLLECTION_NAME = "faq-embeddings";

    /** 유사도 검색의 기본 결과 수. */
    public static final int DEFAULT_TOP_K = 5;

    // ── LLM 기본값 ──────────────────────────────────────────────────

    /** 기본 채팅 모델. */
    public static final String DEFAULT_CHAT_MODEL = "gpt-4.1-nano";

    /** 기본 임베딩 모델. */
    public static final String DEFAULT_EMBEDDING_MODEL = "text-embedding-3-small";

    /** 결정론적 답변을 위한 기본 temperature. */
    public static final double DEFAULT_TEMPERATURE = 0.1;

    // ── 토큰 비용 추정 (1M 토큰당 USD) ─────────────────────

    /** GPT-4.1-nano 백만 토큰당 입력 비용. */
    public static final double DEFAULT_INPUT_COST = 0.10;

    /** GPT-4.1-nano 백만 토큰당 출력 비용. */
    public static final double DEFAULT_OUTPUT_COST = 0.40;

    /**
     * 단일 LLM 호출의 예상 비용을 계산합니다.
     *
     * @param inputTokens  프롬프트/입력 토큰 수
     * @param outputTokens 완성/출력 토큰 수
     * @return USD 예상 비용
     */
    public static double estimateCost(long inputTokens, long outputTokens) {
        return (inputTokens * DEFAULT_INPUT_COST / 1_000_000.0)
             + (outputTokens * DEFAULT_OUTPUT_COST / 1_000_000.0);
    }
}

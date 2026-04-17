package com.jaeyeonling.stage2.m1;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Week 02 (인메모리) vs Week 03 (Qdrant) 검색 성능 벤치마크.
 *
 * 임베딩은 실제 OpenAI API를 사용하고, 검색 속도만 분리 측정합니다.
 * 100 / 500 / 1,000 청크에서 각각 검색 latency를 비교합니다.
 *
 * 사전 조건:
 *   - Qdrant 실행 중 (docker compose up -d qdrant)
 *   - OPENAI_API_KEY 환경변수 설정
 */
@SpringBootTest
class PerformanceBenchmarkTest {

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private EmbeddingModel embeddingModel;

    private static final int WARMUP_QUERIES = 3;
    private static final int MEASURE_QUERIES = 10;
    private static final String SAMPLE_QUERY = "반품 정책이 어떻게 되나요?";

    @Test
    @DisplayName("성능 벤치마크 — 인메모리 vs Qdrant (100/500/1000 청크)")
    void benchmark() {
        int[] sizes = {100, 500, 1000};

        System.out.println();
        System.out.println("=== 성능 벤치마크: 인메모리 vs Qdrant ===");
        System.out.println();
        System.out.printf("| %-10s | %-25s | %-25s |%n", "청크 수", "Week 02 (인메모리)", "Week 03 (Qdrant)");
        System.out.printf("|%-12s|%-27s|%-27s|%n", "------------", "---------------------------", "---------------------------");

        for (int size : sizes) {
            List<String> texts = generateChunks(size);

            double inMemoryMs = benchmarkInMemory(texts);
            double qdrantMs = benchmarkQdrant(texts);

            System.out.printf("| %-10s | %-25s | %-25s |%n",
                    size + " 청크",
                    String.format("%.1f ms", inMemoryMs),
                    String.format("%.1f ms", qdrantMs));
        }

        System.out.println();
    }

    /**
     * 인메모리 brute-force 코사인 유사도 검색 벤치마크.
     * Week 01/02의 InMemoryVectorStore와 동일한 로직을 인라인으로 구현.
     */
    private double benchmarkInMemory(List<String> texts) {
        // 임베딩 생성
        List<float[]> vectors = new ArrayList<>();
        for (String text : texts) {
            vectors.add(embeddingModel.embed(text));
        }

        float[] queryVector = embeddingModel.embed(SAMPLE_QUERY);

        // warmup
        for (int i = 0; i < WARMUP_QUERIES; i++) {
            bruteForceSearch(vectors, texts, queryVector, 5);
        }

        // measure (검색만 — 임베딩 제외)
        long start = System.nanoTime();
        for (int i = 0; i < MEASURE_QUERIES; i++) {
            bruteForceSearch(vectors, texts, queryVector, 5);
        }
        long elapsed = System.nanoTime() - start;

        return (elapsed / 1_000_000.0) / MEASURE_QUERIES;
    }

    /**
     * Qdrant HNSW 인덱스 검색 벤치마크.
     */
    private double benchmarkQdrant(List<String> texts) {
        // Qdrant에 저장
        List<Document> documents = texts.stream()
                .map(Document::new)
                .toList();
        vectorStore.add(documents);

        // warmup
        for (int i = 0; i < WARMUP_QUERIES; i++) {
            vectorStore.similaritySearch(
                    SearchRequest.builder().query(SAMPLE_QUERY).topK(5).build());
        }

        // measure (검색만 — 임베딩은 query embedding 1회 포함, Qdrant 내부 처리)
        long start = System.nanoTime();
        for (int i = 0; i < MEASURE_QUERIES; i++) {
            vectorStore.similaritySearch(
                    SearchRequest.builder().query(SAMPLE_QUERY).topK(5).build());
        }
        long elapsed = System.nanoTime() - start;

        return (elapsed / 1_000_000.0) / MEASURE_QUERIES;
    }

    // --- 인메모리 brute-force 구현 (Week 01 InMemoryVectorStore와 동일) ---

    private List<String> bruteForceSearch(List<float[]> vectors, List<String> texts, float[] queryVector, int topK) {
        record Scored(String text, double score) {}
        List<Scored> scored = new ArrayList<>();
        for (int i = 0; i < vectors.size(); i++) {
            scored.add(new Scored(texts.get(i), cosineSimilarity(vectors.get(i), queryVector)));
        }
        scored.sort((a, b) -> Double.compare(b.score(), a.score()));
        return scored.stream().limit(topK).map(Scored::text).toList();
    }

    private static double cosineSimilarity(float[] a, float[] b) {
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0.0 || normB == 0.0) return 0.0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * FAQ 스타일의 합성 청크를 생성합니다.
     */
    private List<String> generateChunks(int count) {
        String[] topics = {
                "반품 정책", "배송 기간", "결제 방법", "고객 지원", "보증 기간",
                "할인 프로그램", "적립 포인트", "구독 서비스", "기프트 카드", "회원 등급",
                "제품 카테고리", "주문 취소", "교환 절차", "개인정보 보호", "앱 지원",
                "해외 배송", "매장 안내", "이벤트 일정", "파트너십 프로그램", "접근성 지원"
        };

        Random random = new Random(42);
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String topic = topics[i % topics.length];
            chunks.add(String.format("### %s FAQ #%d\n%s에 대한 자주 묻는 질문입니다. " +
                            "초록 코퍼레이션은 고객 만족을 최우선으로 합니다. " +
                            "자세한 내용은 고객센터(%d-%04d)로 문의하세요.",
                    topic, i + 1, topic, 1500 + random.nextInt(100), random.nextInt(10000)));
        }
        return chunks;
    }
}

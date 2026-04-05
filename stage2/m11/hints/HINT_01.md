# 힌트 01: 상담 로그 패턴 감지

## 반복 질문 찾기

7일 내에 같은 intent로 3회 이상 들어온 질문 그룹을 찾습니다.

```java
@Service
public class PatternDetector {

    public List<RepeatedPattern> detect(List<ChatLog> recentLogs, int minCount, int days) {
        LocalDate cutoff = LocalDate.now().minusDays(days);

        return recentLogs.stream()
            // 기간 필터
            .filter(log -> log.date().isAfter(cutoff))
            // intent 기준 그룹화
            .collect(Collectors.groupingBy(ChatLog::primaryIntent))
            .entrySet().stream()
            // 최소 횟수 이상만
            .filter(e -> e.getValue().size() >= minCount)
            // 패턴 객체 생성
            .map(e -> new RepeatedPattern(
                e.getKey(),
                e.getValue(),
                e.getValue().size()
            ))
            .sorted(Comparator.comparingInt(RepeatedPattern::count).reversed())
            .toList();
    }
}

public record RepeatedPattern(
    String intent,
    List<ChatLog> cases,
    int count
) {}
```

## 벡터 기반 유사 질문 클러스터링

intent가 없거나 부정확한 경우, 질문 벡터 유사도로 클러스터링합니다:

```java
public List<List<ChatLog>> clusterBySimilarity(List<ChatLog> logs, double threshold) {
    // 각 고객 질문을 임베딩
    Map<ChatLog, float[]> embeddings = logs.stream()
        .collect(Collectors.toMap(
            log -> log,
            log -> embeddingModel.embed(extractCustomerQuestion(log))
        ));

    // 유사도 임계값 이상이면 같은 클러스터
    List<List<ChatLog>> clusters = new ArrayList<>();

    for (ChatLog log : logs) {
        boolean added = false;
        for (List<ChatLog> cluster : clusters) {
            float[] clusterCenter = embeddings.get(cluster.get(0));
            float[] logVector = embeddings.get(log);
            if (cosineSimilarity(clusterCenter, logVector) >= threshold) {
                cluster.add(log);
                added = true;
                break;
            }
        }
        if (!added) clusters.add(new ArrayList<>(List.of(log)));
    }

    return clusters.stream()
        .filter(c -> c.size() >= 3)  // 3개 이상 클러스터만
        .toList();
}
```

## 패턴 분석 스케줄러

```java
@Scheduled(cron = "0 0 1 * * MON")  // 매주 월요일 새벽 1시
public void weeklyPatternAnalysis() {
    List<ChatLog> recentLogs = chatLogRepository.findLastNDays(7);
    List<RepeatedPattern> patterns = detector.detect(recentLogs, 3, 7);

    log.info("주간 패턴 분석: {}개 반복 패턴 발견", patterns.size());
    patterns.forEach(p ->
        log.info("  - {} ({}회): {}",
            p.intent(), p.count(),
            p.cases().get(0).customerQuestion())
    );

    // 패턴별 FAQ 후보 생성 트리거
    patterns.forEach(faqGenerator::generate);
}
```

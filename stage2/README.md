# Stage 2: 모듈별 심화

Stage 1에서 만든 챗봇은 동작하지만 완벽하지 않습니다.
검색이 부정확하고, 서버를 끄면 데이터가 날아가고, 비용을 모르고, 답변 형식이 매번 다릅니다.

Stage 2는 그 벽을 하나씩 부수는 과정입니다.

---

## 시작하기 전에

1. **Stage 1을 완료하세요** — 벽을 경험하지 않으면 모듈의 필요성을 모릅니다
2. **[벽 리포트](../stage1/mission/wall-report.md)를 작성하세요** — 아래 표에서 자신의 벽에 해당하는 모듈을 선택합니다
3. **의존 관계를 확인하세요** — 일부 모듈은 선수 모듈이 있습니다

## 벽 → 모듈 매핑

| 이런 벽을 만났다면 | 모듈 | 선수 조건 |
|-------------------|------|-----------|
| 서버 재시작하면 데이터가 날아간다 | [M1: 벡터 DB & ETL](m1/mission/MISSION.md) | — |
| 문서 검색만으론 실시간 데이터를 못 쓴다 | [M4: Tool Use & MCP](m4/mission/MISSION.md) | — |
| API 비용이 예상보다 높다 | [M5: 비용 최적화](m5/mission/MISSION.md) | — |
| 로컬에서만 돌아가고, 배포가 안 된다 | [M7: 인프라](m7/mission/MISSION.md) | — |
| 이전 대화를 기억 못 한다 | [M8: 대화 기억](m8/mission/MISSION.md) | — |
| 응답이 완성될 때까지 기다려야 한다 | [M9: 스트리밍](m9/mission/MISSION.md) | — |
| 답변 형식이 매번 달라서 파싱할 수 없다 | [M10: 구조화 출력](m10/mission/MISSION.md) | — |
| 검색 결과가 top-5엔 있는데 top-1이 아니다 | [M2: 검색 품질](m2/mission/MISSION.md) | M1 |
| 버그가 어디서 발생하는지 추적할 수 없다 | [M3: 관측성 & 평가](m3/mission/MISSION.md) | M1 |
| 하나의 에이전트로 복잡한 작업을 처리할 수 없다 | [M6: 멀티 에이전트](m6/mission/MISSION.md) | M4 |
| 좋은 답변이 다음 검색에 반영되지 않는다 | [M11: 피드백 루프](m11/mission/MISSION.md) | M1 + M3 |

## 의존 관계

```
독립 모듈 (바로 시작 가능)
  M1  M4  M5  M7  M8  M9  M10

의존 모듈
  M1 → M2 (검색 품질)
  M1 → M3 (관측성)
  M4 → M6 (멀티 에이전트)
  M1 + M3 → M11 (피드백 루프)
```

## 진행 방법

```bash
# 1. 미션 읽기
cat stage2/m1/mission/MISSION.md

# 2. 코드 구현 (UnsupportedOperationException을 실제 로직으로 교체)

# 3. 테스트 실행
./gradlew :stage2:m1:test

# 4. 막히면 힌트 열기 (HINT_01 → 02 → 03 순서)
cat stage2/m1/hints/HINT_01.md
```

## 인프라

일부 모듈은 Docker로 외부 서비스를 실행해야 합니다.

```bash
# M1, M2, M3, M5, M7, M11: Qdrant
docker run -d -p 6333:6333 -p 6334:6334 qdrant/qdrant:v1.13.6

# M3: Jaeger (선택)
docker run -d -p 4317:4317 -p 4318:4318 -p 16686:16686 jaegertracing/all-in-one:latest

# M5: Ollama
docker run -d -v ollama:/root/.ollama -p 11434:11434 ollama/ollama

# M7: 전체 스택
cd stage2/m7 && docker compose up -d
```

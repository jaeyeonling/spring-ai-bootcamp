# M1 미션: 벡터 DB & ETL

## Stage 1에서 겪은 벽

Stage 1의 벽 리포트에 이런 내용이 있을 것입니다:

> "데이터 3,100건을 전부 프롬프트에 넣을 수 없다. 토큰이 수백만 개다."
> "파일마다 포맷이 달라서 파싱 코드가 제각각이다."
> "서버 재시작하면 임베딩을 다시 해야 한다. 30분이 걸린다."

이 벽을 해결합니다.

---

## 미션

Stage 1의 `InMemoryVectorStore`를 **Qdrant**로 교체하고,
3개 레이어 데이터를 ETL 파이프라인으로 적재하세요.

### 완료 기준

```
[ ] Qdrant에 3,100+ 문서가 적재되어 있다
[ ] 레이어별 메타데이터 필터링이 가능하다 (layer=faq / policy / chatlog)
[ ] 버전 필터링이 가능하다 (status=current / deprecated)
[ ] 서버 재시작 후 데이터가 유지된다 (PersistenceTest 통과)
[ ] 적재 시간이 서버 시작에 포함되지 않는다 (최초 1회 실행 후 재사용)
```

Stage 1 정확도 측정을 다시 실행하세요. 수치가 바뀌는지 확인하세요.

---

## 설계 포인트

### ETL 파이프라인 구조

```
[Read] 파일 읽기 → [Transform] 청킹 + 메타데이터 추출 → [Write] Qdrant 적재
```

각 단계를 독립적으로 설계하면 레이어별로 다른 전략을 적용하기 쉽습니다.

### 레이어별 전략

| 레이어 | 청킹 전략 | 핵심 메타데이터 |
|--------|----------|----------------|
| Layer 1 (FAQ) | `###` 기준 Q&A 단위 | `layer`, `category`, `source_file` |
| Layer 2 (정책) | 섹션 단위 + YAML frontmatter 파싱 | `layer`, `version`, `status`, `effective_date` |
| Layer 3 (상담 로그) | 대화 단위 또는 turns 분리 | `layer`, `intent`, `resolution`, `agent_accuracy` |

### 핵심 질문

- Layer 2 `deprecated/` 문서를 포함할 것인가? 포함한다면 어떻게 구분할 것인가?
- Layer 3 상담사 오답(`agent_accuracy: wrong`)을 포함할 것인가?
- 청크 크기를 어떻게 결정할 것인가? 너무 크면 검색이 뭉개지고, 너무 작으면 컨텍스트가 끊긴다.

이 결정들이 M2 검색 품질에 직접 영향을 미칩니다.

---

## 환경 설정

```yaml
# docker-compose.yml
services:
  qdrant:
    image: qdrant/qdrant:v1.13.6
    ports:
      - "6333:6333"
    volumes:
      - qdrant_data:/qdrant/storage
```

```yaml
# application.yml
spring:
  ai:
    vectorstore:
      qdrant:
        host: localhost
        port: 6334
        collection-name: cholog-faq
        initialize-schema: true
```

---

## 힌트

| 힌트 | 이럴 때 열어보세요 |
|------|-------------------|
| `HINT_01.md` | 벡터 DB가 뭔지, 왜 인메모리와 다른지 이해가 안 될 때 |
| `HINT_02.md` | Qdrant 연결은 됐는데 적재 코드를 어떻게 구조화할지 모를 때 |
| `HINT_03.md` | 멀티 포맷 파싱 (YAML frontmatter, JSONL)이 막힐 때 |

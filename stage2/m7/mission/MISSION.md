# M7 미션: 프로덕션 통합

## 이 모듈의 위치

Stage 1부터 지금까지 만든 것들을 **하나의 운영 가능한 서비스**로 통합합니다.

이 모듈에는 새로운 알고리즘이나 개념이 없습니다.
대신 "실제로 배포하면 어떤 일이 생기는가"를 경험합니다.

---

## 미션

선택한 모듈들을 Docker Compose로 통합하고,
테스트 질문 100개에서 **80% 이상 정확도**를 달성하세요.

### 완료 기준

```
[ ] Docker Compose로 전체 스택 단일 명령 실행
[ ] Stage 1 vs 현재 정확도 비교 (수치로)
[ ] 운영 체크리스트 완료 확인
[ ] README에 배포 방법 문서화
```

---

## Docker Compose 통합

```yaml
# docker-compose.yml
services:
  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      - OPENAI_API_KEY=${OPENAI_API_KEY}
      - SPRING_PROFILES_ACTIVE=prod
    depends_on:
      qdrant:
        condition: service_healthy

  qdrant:
    image: qdrant/qdrant:v1.9.0
    ports:
      - "6333:6333"
    volumes:
      - qdrant_data:/qdrant/storage
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:6333/healthz"]
      interval: 10s

  # M5 선택 시
  ollama:
    image: ollama/ollama:latest
    volumes:
      - ollama_data:/root/.ollama

volumes:
  qdrant_data:
  ollama_data:
```

---

## 운영 체크리스트

배포 전 반드시 확인하세요:

### 보안
```
[ ] API 키가 환경변수로 관리됨 (코드에 하드코딩 없음)
[ ] /actuator 엔드포인트가 외부에 노출되지 않음
[ ] 입력값 검증: 빈 질문, 너무 긴 질문 처리
```

### 안정성
```
[ ] OpenAI API 오류 시 폴백 처리
[ ] Qdrant 연결 실패 시 동작 (graceful degradation)
[ ] 타임아웃 설정 (LLM 호출 최대 30초)
```

### 관측성
```
[ ] /actuator/health 응답 확인
[ ] 로그에 요청/응답 추적 가능
[ ] 비용 추적 활성화 (M3/M5 연동)
```

### 성능
```
[ ] ETL 적재가 서버 시작 시간에 포함되지 않음
[ ] 응답 시간 p95 < 5초 (스트리밍 제외)
[ ] 동시 요청 10개 처리 가능
```

---

## Stage 1 vs 현재 비교

```
Stage 1:
- 정확도: ?% (벽 리포트에서 측정한 수치)
- 요청당 비용: ?원
- 평균 응답 시간: ?ms

현재:
- 정확도: ?% (80% 이상 목표)
- 요청당 비용: ?원
- 평균 응답 시간: ?ms

개선폭: 정확도 +?%p, 비용 ?% 절감, 응답 시간 ?% 변화
```

이 비교가 커리큘럼 전체의 성과 요약입니다.

---

## 힌트

| 힌트 | 이럴 때 열어보세요 |
|------|-------------------|
| `HINT_01.md` | Docker Compose 멀티 서비스 설정이 막힐 때 |
| `HINT_02.md` | Spring Boot 프로덕션 프로파일 설정이 막힐 때 |

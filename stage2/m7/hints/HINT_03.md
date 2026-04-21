# 힌트 03: 통합 실행과 검증

Docker Compose와 Spring Profile을 연결합니다.

## Profile 활성화

Docker Compose에서 Spring Profile을 활성화하는 방법:

```yaml
# docker-compose.yml
services:
  app:
    environment:
      - SPRING_PROFILES_ACTIVE=prod  # ← application-prod.yml 활성화
```

로컬 개발 시에는 IDE에서 `-Dspring.profiles.active=dev`를 설정하거나,
`application-dev.yml`이 기본으로 적용되도록 `application.yml`에 지정합니다:

```yaml
# application.yml
spring:
  profiles:
    active: dev  # 기본값 — Docker에서 환경변수로 덮어씀
```

## Qdrant Health Indicator

Spring Boot Actuator의 `/actuator/health`에 Qdrant 상태를 포함시킬 수 있습니다.

핵심 아이디어:
- `HealthIndicator` 인터페이스를 구현
- Qdrant의 `/healthz` REST 엔드포인트로 상태 확인
- gRPC 포트(6334)와 REST 포트(6333)가 다르므로 별도 프로퍼티로 관리

```yaml
# application.yml — 헬스체크용 REST 포트를 별도로 관리
app:
  qdrant:
    health-url: http://${spring.ai.vectorstore.qdrant.host:localhost}:6333/healthz
```

구현은 HealthCheckConfig.java의 TODO를 참고하세요.

## 배포 후 검증 체크리스트

```bash
# 1. 서비스 시작
docker compose up -d

# 2. 서비스 상태 확인 (Qdrant healthy → app 시작)
docker compose ps

# 3. 헬스체크 — Qdrant 포함 여부 확인
curl -s http://localhost:8080/actuator/health | python3 -m json.tool

# 4. ETL 상태 확인 — 로그에서 "ETL 건너뜀" 또는 "ETL 시작" 확인
docker compose logs app | grep -i etl

# 5. 기본 동작 확인
curl -s -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"question": "반품 기간이 얼마나 되나요?"}'
```

## 흔한 실수

| 증상 | 원인 | 해결 |
|------|------|------|
| app이 Qdrant에 연결 실패 | `localhost` 사용 | `qdrant` (서비스명) 사용 |
| ETL이 매번 재실행됨 | volume이 없음 | `qdrant_data` volume 확인 |
| `.env` 변수가 안 먹힘 | docker compose 경로 불일치 | `.env`가 `docker-compose.yml`과 같은 디렉토리에 있는지 확인 |

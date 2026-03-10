# Spring AI Bootcamp

> **경고**: 이 커리큘럼은 현재 개발 중이며 아직 완벽히 검증되지 않았습니다. 코드, 힌트, 미션 내용이 변경될 수 있습니다.

**Spring 생태계 백엔드 개발자를 위한 Applied GenAI 실습 커리큘럼.**

하나의 FAQ 챗봇이 10주에 걸쳐 프로덕션 AI 서비스로 진화합니다.

## 철학

```
"왜 필요한가?" 가 "어떻게 동작하는가?" 보다 먼저다.

매 주:
  [미션 수령] -> [벽에 부딪힘] -> [힌트 카드 오픈] -> [개념 학습] -> [미션 완료]
```

이론 먼저가 아닙니다. 벽에 부딪혀서 "이게 왜 필요하지?"를 먼저 느끼고, 그 다음에 개념을 배웁니다.

## 시작하기 전에

### 필요한 것

| 항목 | 최소 요구사항 | 비고 |
|------|-------------|------|
| Java | 17 이상 | `java -version`으로 확인 |
| Docker | Docker Desktop 설치 | Week 03부터 Qdrant 실행에 필요 |
| OpenAI API 키 | 유료 계정 필요 | [platform.openai.com](https://platform.openai.com)에서 발급 |
| IDE | IntelliJ IDEA 권장 | VS Code + Java Extension Pack도 가능 |

### 사전 지식

- Spring Boot로 REST API를 만들어 본 경험
- Gradle 멀티 모듈 프로젝트 기본 이해
- AI/ML 사전 지식은 **불필요** (커리큘럼에서 다룹니다)

## 빠른 시작

```bash
# 1. 저장소 클론
git clone https://github.com/jaeyeonling/spring-ai-bootcamp.git
cd spring-ai-bootcamp

# 2. API 키 설정 (둘 중 하나 선택)

# 방법 A: 환경변수 직접 설정
export OPENAI_API_KEY=sk-your-key-here

# 방법 B: .env 파일 사용 (IntelliJ EnvFile 플러그인 필요)
cp .env.example .env
# .env 파일을 열어 OPENAI_API_KEY 값을 입력하세요

# 3. 전체 빌드 확인
./gradlew clean compileJava

# 4. Week 01 미션 시작!
# 먼저 week01/mission/MISSION.md를 읽은 후 코드를 수정하세요.
# 구현 후 테스트 실행:
./gradlew :week01:test
```

> **팁**: `./gradlew :week01:bootRun`으로 애플리케이션을 실행하면 `http://localhost:8080`에서 챗봇을 테스트할 수 있습니다.

## 미션 진행 방법

### 1단계: 미션 읽기

각 주차의 `mission/MISSION.md`를 읽으세요. 요구사항과 완료 기준이 적혀 있습니다.
이론 설명은 없습니다 — 의도적입니다.

### 2단계: 스켈레톤 코드 수정

`src/main/java/` 아래에 `TODO` 마커가 있는 스켈레톤 코드가 있습니다.
`throw new UnsupportedOperationException("구현하세요")`로 표시된 메서드를 구현하세요.

### 3단계: 막히면 힌트 열기

`hints/` 폴더에 3장의 힌트 카드가 있습니다:

| 힌트 | 내용 | 언제 열어야 하나? |
|------|------|-----------------|
| `HINT_01.md` | 핵심 개념 설명 | "뭘 해야 하는지는 알겠는데, 개념을 모르겠다" |
| `HINT_02.md` | 구현 패턴과 예제 | "개념은 이해했는데, 코드로 어떻게 옮기지?" |
| `HINT_03.md` | 거의 정답에 가까운 코드 | "정말 막혔다. 정답을 보여줘" |

가능하면 HINT_01만 보고 구현해보세요. 일찍 힌트를 열수록 학습 효과가 줄어듭니다.

### 4단계: 테스트로 검증

```bash
./gradlew :week01:test   # week01 테스트 실행
./gradlew :week03:test   # week03 테스트 실행 (Qdrant Docker 필요)
```

모든 테스트가 통과하면 해당 주차 미션 완료입니다.

## 프로젝트 구조

```
spring-ai-bootcamp/
├── shared/              # 공통 상수, 유틸리티 (FaqConstants.java)
├── data/                # FAQ 문서 + 테스트 질문 (챗봇의 지식 베이스)
│   ├── faq.md           # 초록 코퍼레이션 FAQ (영문)
│   └── test_questions.json  # 평가용 30개 질문
│
├── week01/              # ── Level 1: 일단 돌아가게 만들어라 ──
├── week02/
├── week03/
│
├── week04/              # ── Level 2: 프로덕션 벽 ──
├── week05/
├── week06/
├── week07/
│
├── week08/              # ── Level 3: AI-native 서비스 ──
├── week09/
└── week10/
```

각 주차 디렉터리 구조:

```
weekNN/
├── mission/
│   └── MISSION.md           # 미션 브리프 (요구사항, 완료 기준)
├── hints/
│   ├── HINT_01.md           # 개념 힌트
│   ├── HINT_02.md           # 구현 패턴 힌트
│   └── HINT_03.md           # 정답에 가까운 코드 힌트
├── src/
│   ├── main/java/           # TODO 마커가 있는 스켈레톤 코드
│   ├── main/resources/      # application.yml 설정
│   └── test/java/           # 미션 완료 검증 테스트
├── build.gradle             # 모듈별 의존성
├── docker-compose.yml       # (일부 주차) 인프라 실행용
└── k8s/                     # (week06) Kubernetes 매니페스트
```

## 기술 스택

| 레이어 | 기술 | 사용 주차 |
|--------|------|-----------|
| Framework | Spring Boot 3.4.5 / Spring AI 1.1.2 | 전체 |
| Build | Gradle 8.5 (멀티 모듈) | 전체 |
| LLM | OpenAI GPT-4o-mini | 전체 |
| LLM (로컬) | Ollama (llama3.2, nomic-embed-text) | 06, 10 |
| Vector Store | 커스텀 인메모리 구현 | 01-02 |
| Vector Store | Qdrant (Docker) | 03-07, 09-10 |
| Observability | Micrometer + OpenTelemetry + Actuator | 05, 10 |
| Tracing | Jaeger (OTLP) | 05, 10 |
| MCP | Spring AI MCP Boot Starter | 09-10 |
| Testing | JUnit 5 + AssertJ | 전체 |

## 10주 커리큘럼

### Level 1: 일단 돌아가게 만들어라 (Week 01-03)

| 주차 | 부딪히는 벽 | 배우는 개념 | 주요 Spring AI 기능 |
|------|------------|------------|---------------------|
| [01](week01/mission/MISSION.md) | 토큰 한계, 키워드 검색 실패 | 임베딩, 유사도, RAG 기초 | `ChatClient`, `EmbeddingModel` |
| [02](week02/mission/MISSION.md) | 낮은 답변 품질, 메트릭 없음 | 청킹 전략, 프롬프트 엔지니어링, 자동 평가 | `TokenTextSplitter`, `RelevancyEvaluator` |
| [03](week03/mission/MISSION.md) | 느린 검색, 재시작 시 데이터 손실 | 벡터 DB, ETL 파이프라인 | `QdrantVectorStore`, ETL pipeline |

### Level 2: 프로덕션 벽 (Week 04-07)

| 주차 | 부딪히는 벽 | 배우는 개념 | 주요 Spring AI 기능 |
|------|------------|------------|---------------------|
| [04](week04/mission/MISSION.md) | top-5엔 있는데 top-1이 아님 | ReRanker, 쿼리 변환, 하이브리드 검색 | Strategy 패턴, ChatClient 직접 변환 |
| [05](week05/mission/MISSION.md) | 버그 위치를 추적할 수 없음 | 관찰가능성, 분산 트레이싱, 환각 탐지 | Micrometer, OTel, Actuator |
| [06](week06/mission/MISSION.md) | API 비용 폭발 | 로컬 모델, 모델 라우팅, K8s 배포 | Ollama, Kubernetes |
| [07](week07/mission/MISSION.md) | 문서 검색만으론 부족, 실시간 데이터 필요 | 함수 호출, 도구 통합, Reflection 패턴 | `@Tool`, `MethodToolCallbackProvider` |

### Level 3: AI-native 서비스 (Week 08-10)

| 주차 | 부딪히는 벽 | 배우는 개념 | 주요 Spring AI 기능 |
|------|------------|------------|---------------------|
| [08](week08/mission/MISSION.md) | 단일 에이전트로는 복잡한 작업 불가 | 멀티 에이전트 워크플로우 | Chain / Routing / Orchestrator 패턴 |
| [09](week09/mission/MISSION.md) | IDE에서 직접 쓰고 싶다 | MCP 프로토콜, 표준화된 도구 인터페이스 | `@Tool` + MCP Boot Starter |
| [10](week10/mission/MISSION.md) | "프로덕션에 배포하세요" | 전체 통합, 운영 준비 | Week 01-09 전체 |

## 주차별 인프라 요구사항

일부 주차는 Docker로 외부 서비스를 실행해야 합니다:

```bash
# Week 03-07, 09: Qdrant 벡터 DB
docker run -d -p 6333:6333 -p 6334:6334 qdrant/qdrant

# Week 05: Jaeger 트레이싱 (선택)
docker run -d -p 4317:4317 -p 4318:4318 -p 16686:16686 jaegertracing/all-in-one:latest

# Week 06: Ollama 로컬 LLM
docker run -d -v ollama:/root/.ollama -p 11434:11434 ollama/ollama
docker exec -it $(docker ps -q -f ancestor=ollama/ollama) ollama pull llama3.2
docker exec -it $(docker ps -q -f ancestor=ollama/ollama) ollama pull nomic-embed-text

# Week 10: 전체 스택 (docker-compose 제공)
cd week10 && docker compose up -d
```

## 자주 묻는 질문

**Q: API 키 없이 시작할 수 있나요?**

Week 01-02의 스켈레톤 코드는 컴파일은 되지만, 실제 실행과 테스트에는 OpenAI API 키가 필요합니다.
Week 06에서 Ollama를 사용하면 로컬에서 무료로 실행할 수 있습니다.

**Q: API 비용은 얼마나 드나요?**

GPT-4o-mini를 사용하므로 비용이 매우 저렴합니다.
10주 전체를 완료해도 일반적으로 $1-5 이내입니다.

**Q: FAQ 데이터가 영어인데 질문은 한국어로 해도 되나요?**

네. GPT-4o-mini는 교차 언어 이해가 가능합니다. 영어 FAQ에 대해 한국어로 질문해도 정상 동작합니다.
`data/test_questions.json`의 30개 평가 질문은 영어로 작성되어 있습니다.

**Q: 순서대로 진행해야 하나요?**

권장합니다. 각 주차가 이전 주차의 코드와 개념 위에 쌓이는 구조입니다.
특히 Week 10 최종 프로젝트는 Week 01-09의 코드를 통합하므로 순차 진행이 필수입니다.

**Q: 10주를 마친 후엔 무엇을 하면 좋나요?**

[NEXT_STEPS.md](NEXT_STEPS.md)를 참고하세요. 이 커리큘럼에서 의도적으로 다루지 않은 영역(비구조화 데이터 RAG, 하이브리드 검색, RAGAS 도입 등)과 탐구 방향이 정리되어 있습니다.

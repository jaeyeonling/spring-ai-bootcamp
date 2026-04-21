# Spring AI Bootcamp

> **경고**: 이 커리큘럼은 현재 개발 중이며 아직 완벽히 검증되지 않았습니다. 코드, 힌트, 미션 내용이 변경될 수 있습니다.

**Spring 생태계 백엔드 개발자를 위한 Applied GenAI 실습 커리큘럼.**

하나의 고객지원 챗봇이 Stage 1에서 시작해, Stage 2 모듈을 거치며 프로덕션 AI 서비스로 진화합니다.

## 철학

```
"왜 필요한가?" 가 "어떻게 동작하는가?" 보다 먼저다.

[Stage 1: 벽에 부딪힘] -> [벽 리포트 작성] -> [Stage 2: 벽을 해결하는 모듈 선택]
```

이론 먼저가 아닙니다. 벽에 부딪혀서 "이게 왜 필요하지?"를 먼저 느끼고, 그 다음에 개념을 배웁니다.

## 시작하기 전에

### 필요한 것

| 항목 | 최소 요구사항 | 비고 |
|------|-------------|------|
| Java | 17 이상 | `java -version`으로 확인 |
| Docker | Docker Desktop 설치 | M1부터 Qdrant 실행에 필요 |
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

# 4. Stage 1 미션 시작!
# 먼저 stage1/mission/MISSION.md를 읽은 후 코드를 수정하세요.
# 구현 후 테스트 실행:
./gradlew :stage1:test
```

> **팁**: `./gradlew :stage1:bootRun`으로 애플리케이션을 실행하면 `http://localhost:8080`에서 챗봇을 테스트할 수 있습니다.

## 커리큘럼 구조

### Stage 1: 고객지원 챗봇 만들기

CTO가 "FAQ 데이터로 챗봇 만들어주세요"라고 했습니다. 추가 안내는 없습니다.
직접 설계하고, 구현하고, 한계를 경험하세요.

완료 후 **벽 리포트**를 작성합니다 — 구현하면서 부딪힌 한계, 해결 못한 것, 개선하고 싶은 것.
이 리포트가 Stage 2에서 어떤 모듈을 선택할지 결정합니다.

### Stage 2: 모듈별 심화

Stage 1에서 겪은 벽을 해결하는 11개 모듈입니다.
[벽 리포트](stage1/mission/wall-report.md)를 작성하면 어떤 모듈을 선택할지 가이드를 받을 수 있습니다.

```
                         ┌─────────┐
                         │ Stage 1 │
                         └────┬────┘
          ┌──────┬──────┬─────┼─────┬──────┬──────┐
          │      │      │     │     │      │      │
         M1     M4     M5    M7    M8     M9    M10
       벡터DB  Tool   비용  인프라  기억  스트림  구조화
          │      │
       ┌──┴──┐   │
       │     │   │
      M2    M3   M6
     검색   관측  멀티에이전트
             │
             │
            M11
          피드백루프 (← M1 + M3 필요)
```

#### 독립 모듈 (Stage 1만 완료하면 바로 시작, 순서 자유)

| 모듈 | 주제 | 해결하는 벽 | 주요 기술 |
|------|------|------------|----------|
| [M1](stage2/m1/mission/MISSION.md) | 벡터 DB & ETL | 재시작 시 데이터 손실, 느린 검색 | Qdrant, ETL 파이프라인 |
| [M4](stage2/m4/mission/MISSION.md) | Tool Use & MCP | 문서 검색만으론 실시간 데이터 불가 | @Tool, MCP Boot Starter |
| [M5](stage2/m5/mission/MISSION.md) | 비용 최적화 | API 비용 폭발 | 시맨틱 캐싱, 모델 라우팅, Ollama |
| [M7](stage2/m7/mission/MISSION.md) | 인프라 | 로컬에서만 되고, 실행 절차가 복잡함 | Docker Compose, 프로파일 분리, ETL 전략 |
| [M8](stage2/m8/mission/MISSION.md) | 대화 기억 | 멀티턴 대화에서 맥락 유실 | ChatMemory, 슬라이딩 윈도우 |
| [M9](stage2/m9/mission/MISSION.md) | 스트리밍 & 응답 제어 | 완성까지 대기, 취소 불가 | SSE 스트리밍, 클라이언트 인터럽트 |
| [M10](stage2/m10/mission/MISSION.md) | 구조화 출력 | LLM 응답 포맷이 매번 다름 | entity() 스키마 강제, 자동 재시도 |

#### 선수 조건이 있는 모듈

| 모듈 | 주제 | 선수 조건 | 해결하는 벽 |
|------|------|----------|------------|
| [M2](stage2/m2/mission/MISSION.md) | 검색 품질 | **M1** | Top-5엔 있는데 Top-1이 아님 |
| [M3](stage2/m3/mission/MISSION.md) | 관측성 & 평가 | **M1** | 버그 위치를 추적할 수 없음 |
| [M6](stage2/m6/mission/MISSION.md) | 멀티 에이전트 | **M4** | 단일 에이전트로 복잡한 작업 불가 |
| [M11](stage2/m11/mission/MISSION.md) | 데이터 피드백 루프 | **M1 + M3** | 좋은 답변이 지식에 반영 안 됨 |

## 미션 진행 방법

### 1단계: 미션 읽기

각 모듈의 `mission/MISSION.md`를 읽으세요. 요구사항과 완료 기준이 적혀 있습니다.
이론 설명은 없습니다 — 의도적입니다.

### 2단계: 스켈레톤 코드 수정

`src/main/java/` 아래에 `TODO` 마커가 있는 스켈레톤 코드가 있습니다.
`throw new UnsupportedOperationException("구현하세요")`로 표시된 메서드를 구현하세요.

### 3단계: 막히면 힌트 열기

`hints/` 폴더에 힌트 카드가 있습니다:

| 힌트 | 내용 | 언제 열어야 하나? |
|------|------|-----------------|
| `HINT_01.md` | 핵심 개념 설명 | "뭘 해야 하는지는 알겠는데, 개념을 모르겠다" |
| `HINT_02.md` | 구현 패턴과 예제 | "개념은 이해했는데, 코드로 어떻게 옮기지?" |
| `HINT_03.md` | 거의 정답에 가까운 코드 | "정말 막혔다. 정답을 보여줘" |

가능하면 HINT_01만 보고 구현해보세요. 일찍 힌트를 열수록 학습 효과가 줄어듭니다.

### 4단계: 테스트로 검증

```bash
./gradlew :stage1:test      # Stage 1 테스트
./gradlew :stage2:m1:test   # M1 모듈 테스트
```

### 5단계: 평가 기록

테스트 통과 후 `EVALUATION.md`를 작성하세요. "무엇이 바뀌었는지"를 수치로 기록합니다.
자세한 형식은 [평가 가이드](docs/EVALUATION_GUIDE.md)를 참고하세요.

## 프로젝트 구조

```
spring-ai-bootcamp/
├── shared/              # 공통 상수, 유틸리티 (FaqConstants.java)
├── data/                # FAQ 문서 + 테스트 질문 (챗봇의 지식 베이스)
│   ├── layer1_faq/      # 공식 FAQ 문서 (영문)
│   ├── layer2_policies/ # 사내 정책 문서 (영문, 버전 포함)
│   └── layer3_chatlogs/ # 고객 상담 로그 (한국어, JSONL)
│
├── stage1/              # ── 고객지원 챗봇 만들기 ──
│
└── stage2/              # ── 모듈별 심화 ──
    ├── m1/              # 벡터 DB & ETL
    ├── m2/              # 검색 품질
    ├── m3/              # 관측성 & 평가
    ├── m4/              # Tool Use & MCP
    ├── m5/              # 비용 최적화
    ├── m6/              # 멀티 에이전트
    ├── m7/              # 인프라
    ├── m8/              # 대화 기억
    ├── m9/              # 스트리밍 & 응답 제어
    ├── m10/             # 구조화 출력
    └── m11/             # 데이터 피드백 루프
```

각 모듈 디렉터리 구조:

```
stage2/mN/
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
└── build.gradle             # 모듈별 의존성
```

## 기술 스택

| 레이어 | 기술 | 사용 모듈 |
|--------|------|-----------|
| Framework | Spring Boot 3.4.5 / Spring AI 1.1.2 | 전체 |
| Build | Gradle 8.5 (멀티 모듈) | 전체 |
| LLM | OpenAI GPT-4.1-nano | 전체 |
| LLM (로컬) | Ollama (llama3.2, nomic-embed-text) | M5, M7 |
| Vector Store | 커스텀 인메모리 구현 | Stage 1 |
| Vector Store | Qdrant (Docker) | M1-M7 |
| Observability | Micrometer + OpenTelemetry + Actuator | M3, M7 |
| Tracing | Jaeger (OTLP) | M3, M7 |
| MCP | Spring AI MCP Boot Starter | M4 |
| Testing | JUnit 5 + AssertJ | 전체 |

## 인프라 요구사항

일부 모듈은 Docker로 외부 서비스를 실행해야 합니다:

```bash
# M1-M7: Qdrant 벡터 DB
docker run -d -p 6333:6333 -p 6334:6334 qdrant/qdrant:v1.13.6

# M3: Jaeger 트레이싱 (선택)
docker run -d -p 4317:4317 -p 4318:4318 -p 16686:16686 jaegertracing/all-in-one:latest

# M5: Ollama 로컬 LLM
docker run -d -v ollama:/root/.ollama -p 11434:11434 ollama/ollama
docker exec -it $(docker ps -q -f ancestor=ollama/ollama) ollama pull llama3.2
docker exec -it $(docker ps -q -f ancestor=ollama/ollama) ollama pull nomic-embed-text

# M7: 전체 스택 (docker-compose 제공)
cd stage2/m7 && docker compose up -d
```

## 자주 묻는 질문

**Q: API 키 없이 시작할 수 있나요?**

Stage 1의 스켈레톤 코드는 컴파일은 되지만, 실제 실행과 테스트에는 OpenAI API 키가 필요합니다.
M5에서 Ollama를 사용하면 로컬에서 무료로 실행할 수 있습니다.

**Q: API 비용은 얼마나 드나요?**

GPT-4.1-nano를 사용하므로 비용이 매우 저렴합니다.
전체를 완료해도 일반적으로 $1-5 이내입니다.

**Q: FAQ 데이터가 영어인데 질문은 한국어로 해도 되나요?**

네. GPT-4.1-nano는 교차 언어 이해가 가능합니다. 영어 FAQ에 대해 한국어로 질문해도 정상 동작합니다.

**Q: 순서대로 진행해야 하나요?**

Stage 1은 필수입니다. Stage 2 모듈은 의존성이 없는 경우 자유 선택 가능합니다.
모듈 간 선수 조건은 각 미션 문서에 명시되어 있습니다.

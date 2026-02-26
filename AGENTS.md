# Spring AI Bootcamp

Spring 생태계 백엔드 개발자를 위한 Applied GenAI 10주 실습 커리큘럼.
하나의 FAQ 챗봇이 10주에 걸쳐 프로덕션 AI 서비스로 진화합니다.

## Tech Stack

- Language: Java 17
- Build: Gradle 8.5 (Groovy DSL) - Multi-module
- Framework: Spring Boot 3.4.5 / Spring AI 1.1.2
- LLM: OpenAI GPT-4o-mini (기본), Ollama (Week 06, 10)
- Embedding: text-embedding-3-small
- Vector Store: 커스텀 인메모리 (Week 01-02), Qdrant (Week 03-10)
- Testing: JUnit 5 + AssertJ
- Package: com.jaeyeonling (전체 — shared, week01~week10)

## Commands

```bash
# 전체 빌드
./gradlew clean compileJava

# 특정 주차 테스트
./gradlew :week01:test
./gradlew :week03:test

# 특정 주차 실행
./gradlew :week01:bootRun

# Docker (Week 03+): Qdrant
docker run -d -p 6333:6333 -p 6334:6334 qdrant/qdrant

# Docker (Week 05): Jaeger
docker run -d -p 4317:4317 -p 4318:4318 -p 16686:16686 jaegertracing/all-in-one:latest

# Docker (Week 06): Ollama
docker run -d -v ollama:/root/.ollama -p 11434:11434 ollama/ollama

# Week 10: 전체 스택
cd week10 && docker compose up -d
```

## Project Structure

```
spring-ai-bootcamp/
├── shared/                  # 공통 상수 (FaqConstants.java)
├── data/                    # FAQ 문서 + 테스트 질문
│   ├── faq.md               # 초록 코퍼레이션 FAQ (영문, 24개 Q&A)
│   ├── faq_ko_reference.md  # 한국어 참조
│   └── test_questions.json  # 평가용 30개 질문
│
├── week01/                  # 임베딩, 코사인 유사도, 수동 RAG
├── week02/                  # 청킹 전략, 프롬프트 엔지니어링, 자동 평가
├── week03/                  # Qdrant 벡터 DB, ETL 파이프라인
├── week04/                  # ReRanker, 쿼리 변환, 하이브리드 검색
├── week05/                  # 관측성 (Micrometer, OTel, Jaeger)
├── week06/                  # 로컬 모델 (Ollama), 모델 라우팅, K8s
├── week07/                  # Function Calling (@Tool), Reflection 패턴
├── week08/                  # 멀티 에이전트 워크플로우
├── week09/                  # MCP 서버 (Claude Desktop/Cursor 연동)
└── week10/                  # 전체 통합 프로덕션 배포
```

각 주차 디렉터리 구조:

```
weekNN/
├── mission/MISSION.md       # 미션 브리프 (요구사항, 완료 기준)
├── hints/
│   ├── HINT_01.md           # 핵심 개념 힌트
│   ├── HINT_02.md           # 구현 패턴 힌트
│   └── HINT_03.md           # 거의 정답 코드 힌트
├── src/main/java/           # TODO가 있는 스켈레톤 코드
├── src/main/resources/      # application.yml
├── src/test/java/           # 인수 테스트 (통과 = 미션 완료)
└── build.gradle             # 모듈별 의존성
```

## Learning Modules

```
Week 01: 임베딩, 코사인 유사도, 수동 RAG         - ChatClient, EmbeddingModel
Week 02: 청킹, 프롬프트 엔지니어링, 자동 평가      - TokenTextSplitter, RelevancyEvaluator
Week 03: Qdrant 벡터 DB, ETL 파이프라인           - QdrantVectorStore, ETL pipeline
Week 04: ReRanker, 쿼리 변환, 하이브리드 검색      - Strategy 패턴, ChatClient 변환
Week 05: 관측성, 분산 트레이싱, 환각 탐지          - Micrometer, OTel, Actuator
Week 06: 로컬 모델, 모델 라우팅, K8s 배포          - Ollama, Kubernetes
Week 07: Function Calling, Reflection 패턴        - @Tool, MethodToolCallbackProvider
Week 08: 멀티 에이전트 (Chain/Routing/Orchestrator) - CompletableFuture, Agent 패턴
Week 09: MCP 서버, 표준화된 도구 인터페이스          - MCP Boot Starter
Week 10: 전체 통합, 프로덕션 배포                    - Docker Compose, 전체 스택
```

## Code Style

- 학습용이므로 성능보다 가독성/명확성 우선
- TODO 마커 위치에 구현 코드 작성
- `throw new UnsupportedOperationException("구현하세요")`를 실제 구현으로 교체
- Spring AI의 공식 API와 패턴 사용 권장
- 한국어 주석 사용 가능 (FAQ는 영어, 질문은 한국어 가능)

## IMPORTANT

- 학습자가 **직접** 구현하는 것이 목표 — 정답을 바로 제공하지 않음
- 힌트는 3단계로 제공: 개념 → 구현 패턴 → 거의 정답 코드
- **테스트 통과가 미션 완료의 기준** (`./gradlew :weekNN:test`)
- FAQ 데이터는 영어, 테스트 질문은 한국어 — cross-language 임베딩 검증
- OpenAI API 키 필요 (OPENAI_API_KEY 환경변수)
- Week 03부터 Docker 필요 (Qdrant)
- FaqConstants.java에 공통 상수 정의 (모델명, 경로, 비용 등)

## 학습 도구

```
/learn [week]      - 주차별 학습 시작
/check [week]      - 구현 검증 (테스트 실행)
/hint [topic]      - 단계별 힌트 제공
/mission [week]    - 미션 브리프 확인
/test [target]     - 테스트 실행 및 분석
/debug [error]     - 버그 원인 분석
/explain [topic]   - 개념/코드 설명
/visualize [flow]  - 데이터 흐름 시각화
/review            - 코드 리뷰
/refactor [target] - 리팩토링 분석 및 제안
@tutor             - Spring AI 개념 질문
@checker           - 구현 코드 검증
@explainer         - 코드/개념 설명
@debugger          - 버그 원인 분석
@tester            - 테스트 분석
@code-reviewer     - 코드 리뷰
@refactor          - 리팩토링 제안
```

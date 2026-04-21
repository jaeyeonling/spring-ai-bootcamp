# Spring AI Bootcamp

Spring 생태계 백엔드 개발자를 위한 Applied GenAI 실습 커리큘럼.
하나의 고객지원 챗봇이 Stage 1에서 시작해 Stage 2 모듈을 거치며 프로덕션 AI 서비스로 진화합니다.

## Tech Stack

- Language: Java 17
- Build: Gradle 8.5 (Groovy DSL) - Multi-module
- Framework: Spring Boot 3.4.5 / Spring AI 1.1.2
- LLM: OpenAI GPT-4.1-nano (기본), Ollama (M5, M7)
- Embedding: text-embedding-3-small
- Vector Store: 커스텀 인메모리 (Stage 1), Qdrant (Stage 2)
- Testing: JUnit 5 + AssertJ
- Package: com.jaeyeonling (전체 — shared, stage1, stage2.m1~m11)

## Commands

```bash
# 전체 빌드
./gradlew clean compileJava

# 특정 모듈 테스트
./gradlew :stage1:test
./gradlew :stage2:m1:test

# 특정 모듈 실행
./gradlew :stage1:bootRun

# Docker: Qdrant (M1+)
docker run -d -p 6333:6333 -p 6334:6334 qdrant/qdrant:v1.13.6

# Docker: Jaeger (M3)
docker run -d -p 4317:4317 -p 4318:4318 -p 16686:16686 jaegertracing/all-in-one:latest

# Docker: Ollama (M5)
docker run -d -v ollama:/root/.ollama -p 11434:11434 ollama/ollama
```

## Project Structure

```
spring-ai-bootcamp/
├── shared/                  # 공통 상수 (FaqConstants.java)
├── data/                    # FAQ 문서 + 테스트 질문
│   ├── layer1_faq/          # 공식 FAQ (영문)
│   ├── layer2_policies/     # 사내 정책 (버전 포함)
│   └── layer3_chatlogs/     # 상담 로그 (한국어, JSONL)
│
├── stage1/                  # 고객지원 챗봇 (임베딩, 코사인 유사도, RAG)
│
└── stage2/
    ├── m1/                  # 벡터 DB & ETL (Qdrant, 파이프라인)
    ├── m2/                  # 검색 품질 (ReRanker, 쿼리 변환)
    ├── m3/                  # 관측성 & 평가 (Micrometer, OTel)
    ├── m4/                  # Tool Use & MCP (@Tool, MCP Starter)
    ├── m5/                  # 비용 최적화 (캐싱, Ollama, 라우팅)
    ├── m6/                  # 멀티 에이전트 (Chain/Routing/Orchestrator)
    ├── m7/                  # 인프라 (Docker Compose, 프로파일 분리, ETL 전략)
    ├── m8/                  # 대화 기억 (ChatMemory, 슬라이딩 윈도우)
    ├── m9/                  # 스트리밍 & 응답 제어 (SSE, 인터럽트)
    ├── m10/                 # 구조화 출력 (entity(), 스키마 강제)
    └── m11/                 # 데이터 피드백 루프 (패턴 감지, FAQ 자동 생성)
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
- **테스트 통과가 미션 완료의 기준**
- FAQ 데이터는 영어, 테스트 질문은 한국어 — cross-language 임베딩 검증
- OpenAI API 키 필요 (OPENAI_API_KEY 환경변수)
- M1부터 Docker 필요 (Qdrant)
- FaqConstants.java에 공통 상수 정의 (모델명, 경로, 비용 등)

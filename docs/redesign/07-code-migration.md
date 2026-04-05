# Step 7: 코드 마이그레이션 — 작업 기록

## 개요

기존 week01~10 기반 Gradle 모듈을 Stage 1 + Stage 2(M1~M11) 구조로 재편.
레거시 모듈은 삭제하지 않고 유지 — 커리큘럼 검증 완료 후 main 머지 시 스켈레톤으로 교체 예정.

---

## 변경 사항

### settings.gradle

레거시 모듈(week01~10) 유지 + 신규 모듈 추가.

```groovy
// 신규 추가
include 'stage1'
include 'stage2:m1'   // 벡터 DB & ETL
include 'stage2:m2'   // 검색 품질
include 'stage2:m3'   // 관측성 & 평가
include 'stage2:m4'   // Tool Use & MCP
include 'stage2:m5'   // 비용 최적화
include 'stage2:m6'   // 멀티 에이전트
include 'stage2:m7'   // 프로덕션 통합
include 'stage2:m8'   // 대화 기억
include 'stage2:m9'   // 스트리밍 & 응답 제어
include 'stage2:m10'  // 구조화 출력
include 'stage2:m11'  // 데이터 피드백 루프
```

### 프로젝트 트리 (신규)

```
stage1/
├── build.gradle
└── src/main/java/com/jaeyeonling/week01/   ← week01 소스 복사 (패키지명 유지)

stage2/
├── build.gradle                             ← 공통 부모 설정
├── m1/  build.gradle + src/ ← week03
├── m2/  build.gradle + src/ ← week04
├── m3/  build.gradle + src/ ← week05
├── m4/  build.gradle + src/ ← week09 + week07 일부
├── m5/  build.gradle + src/ ← week06
├── m6/  build.gradle + src/ ← week08
├── m7/  build.gradle + src/ ← week10
├── m8/  build.gradle + src/ ← 신규 스텁
├── m9/  build.gradle + src/ ← 신규 스텁
├── m10/ build.gradle + src/ ← 신규 스텁
└── m11/ build.gradle + src/ ← 신규 스텁
```

---

## 모듈별 매핑

| 모듈 | 소스 | 핵심 의존성 | Java 파일 수 |
|------|------|------------|------------|
| `stage1` | week01 | openai | 5 |
| `stage2:m1` | week03 | openai, qdrant, tika | 4 |
| `stage2:m2` | week04 | openai, qdrant, tika | 6 |
| `stage2:m3` | week05 | openai, qdrant, tika, actuator, otel | 5 |
| `stage2:m4` | week09 + week07 일부 | openai, qdrant, tika, mcp-server | 8 |
| `stage2:m5` | week06 | openai, ollama, qdrant | 2 |
| `stage2:m6` | week08 | openai | 6 |
| `stage2:m7` | week10 | openai, ollama, qdrant, tika, mcp-server, actuator, otel | 16 |
| `stage2:m8` | 신규 스텁 | openai, qdrant | 1 |
| `stage2:m9` | 신규 스텁 | openai | 1 |
| `stage2:m10` | 신규 스텁 | openai | 1 |
| `stage2:m11` | 신규 스텁 | openai, qdrant, tika | 1 |

총 Java 파일: stage1 8개 + stage2 64개 = **72개**

---

## 패키지명 정책

소스 복사 시 패키지명은 변경하지 않았음. 예:
- `stage2/m1/src/main/java/com/jaeyeonling/week03/IngestionService.java`

이유: 검증 단계(Step 8)에서 코드가 작동하는지 확인 후, main 머지 시 스켈레톤으로 교체하면서 일괄 리네이밍 예정.

---

## stage2:m4 특이사항 (week07 + week09 병합)

week07과 week09 모두 `FaqIngestionService`를 포함하여 단순 복사 시 충돌.
week09(MCP 기반)를 베이스로 하고, week07 고유 파일만 추가 복사:

| 추가된 week07 파일 | 내용 |
|--------------------|------|
| `ReflectionService.java` | LLM 자기 검토 패턴 |
| `ChatController.java` | REST 엔드포인트 |
| `ChatService.java` | 채팅 서비스 |

---

## 신규 모듈 (m8~m11) 현황

Application 스텁만 존재. 각 모듈의 실제 구현은 Step 8 검증 이후 진행.

| 모듈 | 구현할 핵심 개념 |
|------|-----------------|
| M8: 대화 기억 | `ChatMemory`, `MessageWindowChatMemory`, 멀티턴 컨텍스트 유지 |
| M9: 스트리밍 | `StreamingChatModel`, Server-Sent Events, `Flux<String>` |
| M10: 구조화 출력 | `BeanOutputConverter`, JSON Schema, `@JsonProperty` |
| M11: 데이터 피드백 | 상담 로그 분석 → FAQ 자동 생성 → Qdrant 업데이트 |

---

## 컴파일 검증

```
./gradlew :stage1:compileJava \
          :stage2:m1:compileJava ... :stage2:m11:compileJava

BUILD SUCCESSFUL
15 actionable tasks: 12 executed, 3 up-to-date
```

모든 12개 신규 모듈 컴파일 성공.

---

## 다음 단계 (Step 8)

실제 Spring AI RAG 파이프라인으로 벽 검증:
1. Qdrant 실행 (Docker)
2. `stage2:m1` ETL로 3-Layer 데이터 적재
3. `stage1` 기본 RAG로 `test_questions.json` 100개 실행
4. tier별 정확도 측정 → medium/hard 25-35% 확인

### 머지 판단 기준

| 조건 | 기준 |
|------|------|
| easy 정확도 | 70%+ |
| medium 정확도 | 25-40% |
| hard 정확도 | 10-25% |
| 전체 컴파일 | 성공 |

기준 충족 시: 스켈레톤 코드로 교체 → `main` 머지

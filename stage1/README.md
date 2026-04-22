# Stage 1: 고객지원 챗봇 만들기

## 사전 조건

- Java 17+
- 환경변수 `OPENAI_API_KEY` 설정 (`.env` 파일 또는 shell export)

```bash
# .env 파일 사용 시 (프로젝트 루트)
cp .env.example .env
# .env 파일을 열어 OPENAI_API_KEY=sk-... 입력
```

## 실행

```bash
# 프로젝트 루트에서
./gradlew :stage1:bootRun
```

서버가 `http://localhost:8080`에서 시작됩니다.

## API 테스트

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"question": "반품은 어떻게 하나요?"}'
```

응답 예시:

```json
{
  "answer": "...",
  "tokenUsage": {
    "promptTokens": 1234,
    "completionTokens": 56,
    "totalTokens": 1290
  }
}
```

## 테스트 실행

```bash
# 단위 테스트 (API 키 불필요)
./gradlew :stage1:test --tests "com.jaeyeonling.stage1.FaqLoaderTest"
./gradlew :stage1:test --tests "com.jaeyeonling.stage1.InMemoryVectorStoreTest"

# 통합 테스트 (API 키 필요 — ChatControllerTest 포함)
./gradlew :stage1:test
```

## 평가

`data/test_questions.json`에 100개의 테스트 질문이 있습니다 (easy 30 / medium 44 / hard 26).

서버를 띄운 상태에서 평가 스크립트를 실행할 수 있습니다:

```bash
cd data

# Python 환경 준비
python -m venv .venv
.venv/bin/pip install openai qdrant-client python-dotenv

# 평가 실행 (judge 모델 gpt-4o-mini 사용, 100문항 기준 약 $0.5~1 추가 비용)
.venv/bin/python evaluate_rag.py
```

## 미션

자세한 미션 내용은 `mission/MISSION.md`를 참고하세요.

## 프로젝트 구조

```
stage1/
├── mission/
│   ├── MISSION.md          # 미션 설명
│   └── wall-report.md      # 벽 리포트 템플릿
├── hints/
│   ├── HINT_01.md ~ HINT_05.md
├── src/
│   ├── main/java/.../stage1/
│   │   ├── Stage1Application.java
│   │   ├── ChatController.java
│   │   ├── ChatService.java
│   │   ├── FaqLoader.java
│   │   └── InMemoryVectorStore.java
│   └── test/java/.../stage1/
│       ├── ChatControllerTest.java
│       ├── FaqLoaderTest.java
│       └── InMemoryVectorStoreTest.java
└── build.gradle
```

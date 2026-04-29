# 학습 가이드

## 진행 흐름

```
미션 읽기 → 환경 설정 → 설계 → 구현 → 검증 → 개선 → 벽 리포트
```

이 사이클을 반복합니다. 한 번에 완성하려 하지 마세요.
작게 동작하는 것부터 만들고, 조금씩 개선하세요.

---

## 단계별 안내

### 1. 미션 읽기

[mission/MISSION.md](mission/MISSION.md)를 읽고 무엇을 만들어야 하는지 파악하세요.

### 2. 환경 설정

```bash
cp .env.example .env
# OPENAI_API_KEY=sk-... 입력

./gradlew clean compileJava
```

IDE에서 실행할 때는 환경변수를 별도로 설정하세요 (Run Configuration 등).

### 3. 설계 & 구현

`src/main/java/com/jaeyeonling/stage1/`에 코드가 있습니다.

무엇이 필요한지, 어떤 순서로 만들지는 직접 결정하세요.

```bash
# 서버 실행
./gradlew bootRun

# 테스트 요청
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"question": "반품은 어떻게 하나요?"}'
```

### 4. 검증

동작하면 품질을 측정하세요.

```bash
# 평가 스크립트 (Python 필요)
cd data
python -m venv .venv
.venv/bin/pip install openai python-dotenv requests
.venv/bin/python evaluate.py
```

서버가 `http://localhost:8080`에서 실행 중이어야 합니다.

이 평가 도구는 가장 기본적인 형태입니다.
LLM 판정 방식, 채점 기준, 평가 범위 모두 손볼 곳이 있습니다.
평가 도구 자체도 벽의 일부입니다 — "이 점수가 정말 정확한가?"도 생각해보세요.

### 5. 개선

정확도가 낮은 질문을 분석하세요:
- 왜 틀렸는가?
- 어떤 데이터가 빠졌는가?
- 프롬프트를 바꾸면 나아지는가?

개선 → 측정을 반복하세요.

### 6. 벽 리포트 작성

[mission/wall-report.md](mission/wall-report.md)를 작성하세요.

---

## 힌트 사용

**가급적이면 힌트를 보지 않고 진행하세요.**

힌트는 답을 주지 않습니다. 막힌 이유를 이해하는 데 도움을 줄 뿐입니다.

진짜 막혔을 때만, 순서대로 하나씩 열어보세요:

| 힌트 | 열어볼 타이밍 |
|------|-------------|
| [HINT_01](hints/HINT_01.md) | "Spring AI로 LLM을 어떻게 호출하지?" |
| [HINT_02](hints/HINT_02.md) | "데이터가 너무 커서 넣을 수가 없는데?" |
| [HINT_03](hints/HINT_03.md) | "데이터 형식이 다양해서 어떻게 읽지?" |
| [HINT_04](hints/HINT_04.md) | "답변이 이상한데 왜 그런지 모르겠다" |
| [HINT_05](hints/HINT_05.md) | "동작은 하는데 맞는지 어떻게 확인하지?" |
| [HINT_06](hints/HINT_06.md) | "돌아는 가는데... 이게 맞나?" |

---

## 2주 페이싱 (권장)

강제가 아닌 권장 일정입니다. 자기 페이스에 맞게 조정하세요.

### 1주차: 동작하게 만들기

- Day 1-2: 환경 설정 + 미션 파악 + 설계
- Day 3-5: 기본 구현 (API가 답변을 반환하는 상태)
- Day 6-7: 첫 품질 측정 + 개선 시도

**1주차 목표**: `curl`로 질문하면 그럴듯한 답변이 온다.

### 2주차: 품질 올리기 + 벽 리포트

- Day 8-10: 실패 분석 + 개선 (데이터 활용, 프롬프트 튜닝)
- Day 11-12: 최종 품질 측정
- Day 13-14: 벽 리포트 작성 + 정리

**2주차 목표**: 정확도 수치를 알고, 왜 그런지 설명할 수 있다.

---

## 실행 방법 요약

```bash
# 서버 실행
./gradlew bootRun

# API 테스트
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"question": "배송은 얼마나 걸리나요?"}'

# 품질 평가 (Python 환경 준비 후)
cd data && .venv/bin/python evaluate.py
```

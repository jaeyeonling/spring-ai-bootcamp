# Stage 1: 고객지원 챗봇 만들기

Spring AI로 FAQ 챗봇을 직접 만드는 실습입니다.

커리큘럼 소개는 [SYLLABUS.md](SYLLABUS.md)를, 진행 방법은 [GUIDE.md](GUIDE.md)를 참고하세요.

---

## 필요한 것

| 항목 | 비고 |
|------|------|
| Java 17+ | `java -version`으로 확인 |
| OpenAI API 키 | [platform.openai.com](https://platform.openai.com)에서 발급 |
| IDE | IntelliJ IDEA 권장 (VS Code + Java Extension Pack도 가능) |

- Spring Boot로 REST API를 만들어 본 경험이 있으면 됩니다
- AI/ML 사전 지식은 없어도 됩니다
- 끝까지 해도 API 비용은 **$1-5** 안쪽입니다 (GPT-4.1-nano)

---

## 빠른 시작

```bash
# 프로젝트 루트에서
cp .env.example .env   # OpenAI API 키 입력
./gradlew :stage1:bootRun
```

서버가 `http://localhost:8080`에서 시작됩니다.

그 다음 [mission/MISSION.md](mission/MISSION.md)를 여세요. 거기서부터 시작입니다.

---

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

---

## 프로젝트 구조

```
stage1/
├── mission/
│   ├── MISSION.md              # 미션 설명 (여기서 시작)
│   └── wall-report.md          # 벽 리포트 (마지막에 작성)
├── hints/
│   ├── HINT_01.md ~ HINT_06.md # 막혔을 때 열어보세요
├── data/
│   ├── layer1_faq/             # 공식 FAQ 문서
│   ├── layer2_policies/        # 사내 정책 문서
│   ├── layer3_chatlogs/        # 고객 상담 로그
│   └── test_questions.json     # 평가용 질문 100개
├── src/
│   └── main/java/com/jaeyeonling/stage1/
│       └── Application.java    # 여기서부터 만드세요
├── SYLLABUS.md                 # 커리큘럼 소개 ← 과정 전체 그림
├── GUIDE.md                    # 진행 가이드 ← 미션 중 참고
└── build.gradle
```

---

## 자주 묻는 질문

**Q: API 키 없이 시작할 수 있나요?**

코드 작성과 컴파일은 가능하지만, 실제 실행에는 OpenAI API 키가 필요합니다.

**Q: FAQ 데이터가 영어인데 질문은 한국어로 해도 되나요?**

네. GPT-4.1-nano는 교차 언어 이해가 가능합니다.

**Q: 어떤 도구를 써도 되나요?**

네. 구현 방법에 제약은 없습니다.

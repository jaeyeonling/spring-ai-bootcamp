# 힌트 01: LLM 호출 — Spring AI로 어떻게 대화하는가

`build.gradle`에 `spring-ai-starter-model-openai`가 있습니다.
API 키도 `application.yml`에 설정되어 있습니다.

그런데 코드에서 어떻게 LLM을 호출하는지 모르겠습니다.

## 증상

- Spring AI를 처음 써본다
- `ChatClient`가 뭔지 모른다
- LLM한테 질문을 보내는 법을 모른다

## 왜 이게 필요한가

Spring AI는 LLM 호출을 Spring 스타일로 추상화합니다.
HTTP 클라이언트로 OpenAI REST API를 직접 호출할 수도 있지만,
Spring AI의 `ChatClient`를 쓰면:
- 모델 설정을 `application.yml`로 관리
- 토큰 사용량을 응답에서 바로 추출
- 프롬프트 구성을 빌더 패턴으로

미션의 제약사항에도 "Spring AI의 `ChatClient`를 통해" 라고 되어 있습니다.

## 다음에 조사해볼 것

- `ChatClient.Builder` — Spring이 자동 주입해주는 빌더
- `.prompt()`, `.system()`, `.user()` — 프롬프트 구성
- `.call()` vs `.stream()` — 동기/비동기 호출
- `ChatResponse` — 응답에서 텍스트와 토큰 사용량 꺼내기

Spring AI 공식 문서에서 "ChatClient" 항목을 찾아보세요.

## 벽 리포트에 기록

LLM을 호출하는 것 자체는 어렵지 않습니다.
어려운 건 그 다음입니다 — 질문에 맞는 답을 하려면 **무엇을** 프롬프트에 넣어야 하는가?

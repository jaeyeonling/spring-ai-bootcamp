# Week 06 미션: "API 비용이 폭발하고 있습니다"

## 상황

FAQ 챗봇이 운영 중이며 잘 작동하고 있습니다. 너무 잘 작동해서 문제입니다.
CTO가 클라우드 청구 대시보드를 꺼냅니다:

> "OpenAI API에 매달 $2,000을 쓰고 있어요. 프로토타입에는 괜찮지만,
> 3분기까지 팀이 10개 더 온보딩될 예정입니다. 매달 $20,000이 될 거예요.
> 금요일까지 비용을 1/10로 줄이거나 외부 API 의존성을 완전히 없애는 계획을 주세요
> — 숫자와 함께."

두 가지 선택지가 있습니다. 하나를 고르거나 (또는 둘 다 제안하세요):

- **옵션 A**: 비용 최적화 — 캐싱, 모델 다운그레이드, 프롬프트 압축
- **옵션 B**: Ollama를 사용한 자체 호스팅 (또는 다른 로컬 추론 엔진)

어느 경우든 배포는 Kubernetes를 지원해야 합니다. 운영 환경에서 `docker-compose up`은 더 이상 안 됩니다.

## 요구사항

### 1. 기술 검토: 추론 엔진

로컬 추론 엔진 비교를 작성하세요. 최소 다음 네 가지를 포함하세요:

| 엔진 | 라이선스 | GPU 필요? | API 호환성 | 최적 용도 |
|--------|---------|---------------|-------------------|----------|
| [Ollama](https://ollama.com) | MIT | 선택적 | OpenAI 호환 | 개발/소규모 팀 |
| [vLLM](https://github.com/vllm-project/vllm) | Apache 2.0 | 필요 | OpenAI 호환 | 고처리량 운영 |
| [TGI](https://github.com/huggingface/text-generation-inference) | Apache 2.0 | 필요 | HF API + OpenAI | HuggingFace 생태계 |
| [SGLang](https://github.com/sgl-project/sglang) | Apache 2.0 | 필요 | OpenAI 호환 | 복잡한 LLM 프로그램 |

각 항목에 대해 다음을 답하세요:
- 설정이 얼마나 어려운가요?
- 어떤 모델을 지원하나요?
- 동등한 하드웨어에서의 처리량(토큰/초)은?
- Spring AI에 직접 통합이 있나요?

### 2. 비용 분석

다음과 같은 비용 비교 표를 만드세요:

| | OpenAI API | 자체 호스팅 (A10G) | 자체 호스팅 (T4) |
|---|---|---|---|
| 월 비용 (현재 볼륨) | $2,000 | ??? | ??? |
| 월 비용 (10배 볼륨) | $20,000 | ??? | ??? |
| GPU 인스턴스 비용 | N/A | ??? | ??? |
| 설정/유지 시간 | 0 | ??? | ??? |
| 손익분기점 | N/A | ??? 요청/월 | ??? 요청/월 |

손익분기점 계산: 어느 요청 볼륨에서 자체 호스팅이 더 저렴해지나요?

### 3. 모델 라우팅 서비스

Spring 프로파일 기반으로 OpenAI와 Ollama 간에 전환할 수 있는
`ModelRoutingService`를 구현하세요. 동일한 애플리케이션 코드가 두 공급자 모두에서
작동해야 합니다 — 활성 프로파일만 변경하면 됩니다.

```
# OpenAI로 실행 (기본)
./gradlew bootRun

# Ollama로 실행
./gradlew bootRun --args='--spring.profiles.active=ollama'
```

### 4. Kubernetes 배포

다음을 배포하는 K8s 매니페스트를 만드세요:
- Spring Boot 챗봇 애플리케이션
- Qdrant 벡터 저장소

매니페스트에는 적절한 리소스 제한과 헬스 체크가 포함되어야 합니다.

> **GPU 노드에 대한 참고**: Kubernetes에서 LLM을 GPU로 실행하는 것 (예: GPU 노드의 Ollama)은
> GPU 디바이스 플러그인과 특정 노드 선택자가 필요한 고급 주제로 이번 주 범위를 벗어납니다.
> 실제로 GPU가 장착된 클러스터에 배포한다면 NVIDIA GPU Operator 문서를 참고하세요.
> 이 실습에서는 CPU 전용 배포를 목표로 합니다.

목표는 작동하는 `kubectl apply --dry-run=client` — 운영 GPU 설정이 아닙니다.

### 완료 기준

- 최소 4개 추론 엔진을 비교하는 기술 검토 문서
- 실제 수치와 손익분기점 계산이 포함된 비용 분석
- `--spring.profiles.active=ollama`와 기본(OpenAI) 모두에서 `ModelRoutingService` 작동
- `kubectl apply --dry-run=client`를 통과하는 `k8s/` 디렉터리의 K8s 매니페스트
- 로컬에서 Ollama를 실행하며 챗봇이 엔드투엔드로 작동

## 시작하는 방법

`src/main/java/com/jaeyeonling/week06/`를 열어보세요 — 스켈레톤 코드가 있습니다.
`// TODO:` 주석을 찾아보세요. 그곳에 코드를 작성하면 됩니다.

권장 순서:
1. 로컬에서 Ollama 실행 (`docker run` 또는 네이티브 설치)
2. Ollama REST API 작동 확인 (`curl http://localhost:11434/api/chat`)
3. Spring 프로파일로 `ModelRoutingService` 연결
4. K8s 매니페스트 작성
5. 비용 분석 및 기술 검토 작성

## 힌트

막혔나요? `hints/` 폴더를 확인하세요 — 단, 먼저 혼자 시도해보세요.

| 힌트 | 이럴 때 열어보세요 |
|------|-------------------|
| `HINT_01.md` | Ollama를 Spring AI와 통합하는 데 도움이 필요할 때 |
| `HINT_02.md` | 이 스택을 위한 K8s 매니페스트 작성 방법을 모를 때 |
| `HINT_03.md` | 비용 분석을 위한 프레임워크가 필요할 때 |

# 힌트 01: MCP 기본

## MCP란?

MCP(Model Context Protocol)는 Anthropic이 만든 개방형 표준으로 AI 어시스턴트가
외부 데이터 소스와 툴에 연결할 수 있게 합니다. **클라이언트-서버 프로토콜**입니다 —
여러분의 애플리케이션이 서버이고, AI 클라이언트(Claude Desktop, Cursor 등)가 연결됩니다.

MCP 이전에는 모든 AI 통합이 커스텀이었습니다:
- Claude 플러그인은 하나의 API 형식
- ChatGPT 플러그인은 다른 형식
- Cursor 확장은 또 다른 형식
- 각각 커스텀 인증, 발견, 호출 로직이 필요

MCP는 이 모든 것을 하나의 프로토콜로 표준화합니다.

## 3가지 프리미티브

MCP는 정확히 세 가지 프리미티브를 정의합니다. 노출하는 모든 것은 이 중 하나를 통해 갑니다:

### 1. Resources (GET 엔드포인트와 유사)

Resources는 AI가 읽을 수 있는 **데이터**를 노출합니다. URI로 식별됩니다.

```
resource://faq/documents       -> 모든 FAQ 문서 목록
resource://faq/categories      -> FAQ 카테고리 목록
resource://config/settings     -> 현재 설정
```

Resources는 **읽기 전용**입니다. AI가 파일을 읽듯이 컨텍스트를 위해 읽습니다.

"여기 필요할 수 있는 데이터가 있습니다"라고 생각하세요.

### 2. Tools (POST 엔드포인트와 유사)

Tools는 AI가 호출할 수 있는 **액션**을 노출합니다. 파라미터를 받고 결과를 반환합니다.

```
tool: searchFaq(query: "환불 정책")     -> 일치하는 FAQ 항목
tool: lookupOrder(orderId: "ORD-123")       -> 주문 상세
tool: reviewCode(diff: "...")               -> 코드 리뷰
```

Tools는 **무언가를 합니다**. 함수 호출과 동등합니다.

"여기 할 수 있는 것이 있습니다"라고 생각하세요.

### 3. Prompts (템플릿과 유사)

Prompts는 AI가 사용할 수 있는 **재사용 가능한 프롬프트 템플릿**을 노출합니다.
호출 시 채워지는 파라미터가 있을 수 있습니다.

```
prompt: customer_support(issue: "청구 문제")
  -> "당신은 도움이 되는 고객지원 담당자입니다. 고객에게 {issue}가 있습니다.
     FAQ 검색 툴을 사용해 관련 답변을 찾으세요..."
```

Prompts는 **워크플로우 스타터**입니다. AI에게 일반적인 작업에 대한 미리 구축된 전략을 제공합니다.

"이런 유형의 작업에 접근하는 방법이 있습니다"라고 생각하세요.

## MCP와 REST 비교

| 측면 | REST API | MCP 서버 |
|--------|----------|------------|
| 발견 | 문서 읽기, 엔드포인트 찾기 | 클라이언트가 툴/리소스/프롬프트 자동 발견 |
| 스키마 | OpenAPI/Swagger (수동) | 어노테이션에서 자동 생성 |
| 인증 | 커스텀 (API 키, OAuth) | 프로토콜에 내장 |
| 클라이언트 코드 | HTTP 호출 작성 | AI가 직접 툴 호출 |
| 전송 | HTTP | stdio 또는 HTTP/SSE |
| 누가 호출하나 | 여러분의 코드 | AI 모델 |

핵심 통찰: REST에서는 **개발자**가 문서를 읽고 클라이언트 코드를 작성합니다.
MCP에서는 **AI 모델**이 툴 설명을 읽고 자동으로 호출합니다.

## Spring AI MCP Boot Starter (1.1.2)

Spring AI 1.1.2는 `spring-ai-starter-mcp-server` 의존성을 제공하며,
`@Tool` 및 `@ToolParam` 어노테이션을 통해 MCP Tool을 등록합니다.

의존성 추가 (`build.gradle`):

```groovy
implementation 'org.springframework.ai:spring-ai-starter-mcp-server'
```

`application.yml` 설정:

```yaml
spring:
  ai:
    mcp:
      server:
        name: my-ai-tools
        version: 1.0.0
```

Tool 등록 방법은 힌트 02를 참고하세요.

## 다음 할 일

개념을 이해했습니다. 이제 만들어봅시다. 구현 상세 —
어노테이션, 서비스 구조, 테스트 — 는 힌트 02를 참고하세요.

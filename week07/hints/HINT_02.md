# 힌트 02: 동적 툴을 위한 FunctionCallback

## @Tool로 부족한 경우

`@Tool`은 컴파일 타임에 모든 툴을 알고 있을 때 작동합니다. 하지만 다음과 같은 경우는?
- 툴이 런타임에 데이터베이스나 설정 파일에서 로드되는 경우?
- 사용자마다 사용 가능한 툴이 다른 경우?
- 프로그래밍 방식으로 툴을 생성하고 싶은 경우?

이런 경우에는 `FunctionCallback`을 사용하세요.

## FunctionCallback.builder()

동적으로 툴 구성:

```java
FunctionCallback dynamicTool = FunctionCallback.builder()
    .function("getWeather", (Request request) -> {
        // 여기에 로직 작성
        return new WeatherResponse(request.city(), 22.5, "맑음");
    })
    .description("도시의 현재 날씨를 조회합니다")
    .inputType(Request.class)
    .build();

// ChatClient에서 사용
String answer = chatClient.prompt()
    .user("서울 날씨는 어때요?")
    .tools(dynamicTool)
    .call()
    .content();
```

`Request` 클래스가 입력 스키마를 정의합니다:

```java
public record Request(
    @JsonProperty(required = true) String city
) {}

public record WeatherResponse(String city, double temperature, String condition) {}
```

## 설정에서 툴 구성

```java
@Service
public class DynamicToolFactory {

    /**
     * 설정 목록에서 툴을 생성합니다.
     * 각 설정 항목은 툴 이름, 설명, HTTP 엔드포인트를 정의합니다.
     */
    public List<FunctionCallback> createTools(List<ToolConfig> configs) {
        return configs.stream()
            .map(config -> FunctionCallback.builder()
                .function(config.name(), input -> callExternalApi(config.endpoint(), input))
                .description(config.description())
                .inputType(Map.class)
                .build())
            .toList();
    }

    private String callExternalApi(String endpoint, Map<String, Object> input) {
        // TODO: 입력 파라미터로 엔드포인트에 HTTP 호출
        throw new UnsupportedOperationException("구현해주세요");
    }
}
```

## ToolCallingManager

`ToolCallingManager` 인터페이스는 Spring AI가 툴 호출 생명주기를 관리하는 방법입니다:

1. 주어진 요청에서 사용 가능한 툴 결정
2. LLM이 요청할 때 툴 실행
3. 결과를 LLM으로 반환

대부분의 경우 기본 `ToolCallingManager`가 모든 것을 처리합니다.
다음을 원할 때만 커스텀이 필요합니다:
- 툴 호출 주변에 로깅/메트릭 추가
- 권한 부여 구현 (사용자 X는 툴 Y만 호출 가능)
- 타임아웃/서킷 브레이커 동작 추가

```java
@Bean
public ToolCallingManager customToolCallingManager() {
    return ToolCallingManager.builder()
        // 기본 구현은 @Tool 메서드와
        // FunctionCallback 기반 툴을 모두 처리합니다
        .build();
}
```

## 언제 무엇을 사용할까요?

| 방식 | 사용 시기 |
|----------|----------|
| `@Tool` 어노테이션 | 컴파일 타임에 알려진 툴, Spring 빈으로 정의 |
| `FunctionCallback.builder()` | 동적 툴, 런타임에 로드 |
| 커스텀 `ToolCallingManager` | 횡단 관심사 필요 (인증, 로깅, 서킷 브레이커) |

이번 주 미션에는 `@Tool`로 충분합니다. 하지만 `FunctionCallback`을 아는 것은
툴이 테넌트 또는 사용자별로 설정되는 실제 시스템에서 유용합니다.

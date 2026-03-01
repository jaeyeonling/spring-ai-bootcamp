package com.jaeyeonling.shared;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.Map;

/**
 * Spring AI 1.1.x의 extra_body 직렬화 버그 workaround.
 *
 * <h3>문제</h3>
 * Spring AI 1.1.0에서 {@code ChatCompletionRequest}의 {@code extraBody} 필드가
 * null일 때 빈 HashMap으로 초기화되도록 변경되었고, 1.1.1에서 {@code @JsonProperty("extra_body")}
 * 어노테이션이 추가되면서 빈 {@code "extra_body": {}}가 모든 Chat Completion 요청에
 * 직렬화됩니다. OpenAI API는 인식하지 못하는 파라미터를 거부하므로 HTTP 400 에러가 발생합니다.
 *
 * <h3>해결</h3>
 * {@link RestClientCustomizer}로 나가는 HTTP 요청의 JSON body에서
 * {@code extra_body} 키를 제거합니다.
 *
 * <h3>제거 시점</h3>
 * Spring AI 1.1.3 또는 2.0.0 GA 릴리스 시 이 클래스를 제거하세요.
 *
 * @see <a href="https://github.com/spring-projects/spring-ai/issues/5196">spring-ai#5196</a>
 * @see <a href="https://github.com/jaeyeonling/spring-ai-bootcamp/issues/1">bootcamp#1</a>
 */
@Configuration
@ConditionalOnClass(name = "org.springframework.ai.openai.api.OpenAiApi")
public class OpenAiExtraBodyWorkaround {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    @Bean
    public RestClientCustomizer openAiExtraBodyStripper(final ObjectMapper objectMapper) {
        return builder -> builder.requestInterceptor((request, body, execution) -> {
            if (body.length > 0) {
                try {
                    final Map<String, Object> map = objectMapper.readValue(body, MAP_TYPE);
                    if (map.remove("extra_body") != null) {
                        body = objectMapper.writeValueAsBytes(map);
                    }
                } catch (final IOException ignored) {
                    // JSON이 아닌 요청(파일 업로드 등)은 원본 그대로 전달
                }
            }
            return execution.execute(request, body);
        });
    }
}

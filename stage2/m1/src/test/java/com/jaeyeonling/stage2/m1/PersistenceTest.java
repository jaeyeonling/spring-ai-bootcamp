package com.jaeyeonling.stage2.m1;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 영속성 검증 테스트 — 인제스트 없이 쿼리만 실행.
 *
 * 이 테스트는 Qdrant에 이미 데이터가 저장되어 있는 상태에서
 * 앱을 재시작해도 쿼리가 작동하는지를 검증합니다.
 *
 * 사용법:
 *   1. 먼저 QueryControllerTest를 실행 (ingest + query)
 *      ./gradlew :stage2:m1:test --tests "*.QueryControllerTest"
 *
 *   2. 그 다음 이 테스트만 실행 (query만 — 새로운 Spring Context)
 *      ./gradlew :stage2:m1:test --tests "*.PersistenceTest"
 *
 *   인메모리였다면 2번에서 실패한다 — 데이터가 없으니까.
 *   Qdrant라면 2번도 통과한다 — Docker 볼륨에 데이터가 살아있으니까.
 *
 * 사전 조건:
 *   - Qdrant가 실행 중 (docker compose up -d qdrant)
 *   - QueryControllerTest가 먼저 실행되어 데이터가 인제스트된 상태
 */
@SpringBootTest
@AutoConfigureMockMvc
class PersistenceTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("영속성 검증 — 인제스트 없이 이전에 저장된 데이터로 쿼리가 작동한다")
    void queryWorksWithoutIngest() throws Exception {
        mockMvc.perform(post("/api/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"question": "반품 정책이 어떻게 되나요?"}
                    """))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.answer").isNotEmpty())
            .andExpect(jsonPath("$.sources").isArray());
    }

    @Test
    @DisplayName("영속성 검증 — 재시작 후에도 30일 반품 정보가 유지된다")
    void persistedDataContainsCorrectContent() throws Exception {
        mockMvc.perform(post("/api/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"question": "상품을 반품할 수 있는 기간은 며칠인가요?"}
                    """))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.answer").value(
                org.hamcrest.Matchers.containsStringIgnoringCase("30")
            ));
    }
}

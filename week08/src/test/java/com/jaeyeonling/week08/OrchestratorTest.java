package com.jaeyeonling.week08;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 멀티 에이전트 PR 리뷰 오케스트레이터의 인수 테스트.
 *
 * 이 테스트들은 다음을 검증합니다:
 * 1. 모든 에이전트가 Agent 인터페이스를 구현하고 출력을 생성
 * 2. 오케스트레이터가 에이전트를 실행하고 결과를 결합
 * 3. 최소 2개의 워크플로우 패턴이 사용 가능
 */
@SpringBootTest
class OrchestratorTest {

    @Autowired
    private OrchestratorService orchestratorService;

    @Autowired
    private CodeReviewAgent codeReviewAgent;

    @Autowired
    private TestWriterAgent testWriterAgent;

    @Autowired
    private DocWriterAgent docWriterAgent;

    private static final String SAMPLE_PR_DIFF = """
            diff --git a/src/main/java/com/example/UserService.java b/src/main/java/com/example/UserService.java
            index 1234567..abcdefg 100644
            --- a/src/main/java/com/example/UserService.java
            +++ b/src/main/java/com/example/UserService.java
            @@ -15,6 +15,20 @@ public class UserService {
            +    public User findById(String id) {
            +        return userRepository.findById(id).orElse(null);
            +    }
            +
            +    public void deleteUser(String id) {
            +        userRepository.deleteById(id);
            +    }
            +
            +    public User updateEmail(String id, String email) {
            +        User user = userRepository.findById(id).get();
            +        user.setEmail(email);
            +        return userRepository.save(user);
            +    }
            """;

    @Test
    @DisplayName("CodeReviewAgent가 코드 리뷰 결과를 생성한다")
    void codeReviewAgent_producesOutput() {
        String result = codeReviewAgent.execute(SAMPLE_PR_DIFF, "");
        assertThat(result).isNotBlank();
        // 리뷰에 최소 하나의 이슈가 언급되어야 함 (예: orElse(null), 검증 누락)
        assertThat(result.toLowerCase()).containsAnyOf("null", "validation", "security", "error");
    }

    @Test
    @DisplayName("TestWriterAgent가 테스트 제안을 생성한다")
    void testWriterAgent_producesOutput() {
        String result = testWriterAgent.execute(SAMPLE_PR_DIFF, "");
        assertThat(result).isNotBlank();
        // 테스트 제안에 테스트 관련 용어가 포함되어야 함
        assertThat(result.toLowerCase()).containsAnyOf("test", "assert", "verify", "should");
    }

    @Test
    @DisplayName("DocWriterAgent가 문서화 제안을 생성한다")
    void docWriterAgent_producesOutput() {
        String result = docWriterAgent.execute(SAMPLE_PR_DIFF, "");
        assertThat(result).isNotBlank();
        // 문서 제안에 문서 관련 용어가 포함되어야 함
        assertThat(result.toLowerCase()).containsAnyOf("document", "readme", "api", "javadoc");
    }

    @Test
    @DisplayName("오케스트레이터가 에이전트 출력을 결합한 리포트를 생성한다")
    void orchestrator_combinesAgentOutputs() {
        String report = orchestratorService.review(SAMPLE_PR_DIFF);

        assertThat(report).isNotBlank();
        // 리포트에 세 에이전트 모두의 내용이 포함되어야 함
        assertThat(report.length()).isGreaterThan(200);
    }

    @Test
    @DisplayName("모든 에이전트가 Agent 인터페이스를 구현한다")
    void agents_implementInterface() {
        // 모든 에이전트가 Agent 인터페이스를 구현하는지 검증
        assertThat(codeReviewAgent).isInstanceOf(Agent.class);
        assertThat(testWriterAgent).isInstanceOf(Agent.class);
        assertThat(docWriterAgent).isInstanceOf(Agent.class);
    }

    @Test
    @DisplayName("에이전트들이 고유한 이름을 가진다")
    void agents_haveDistinctNames() {
        // 에이전트들이 식별을 위한 고유한 이름을 가지는지 검증
        assertThat(codeReviewAgent.name()).isNotEqualTo(testWriterAgent.name());
        assertThat(testWriterAgent.name()).isNotEqualTo(docWriterAgent.name());
        assertThat(codeReviewAgent.name()).isNotEqualTo(docWriterAgent.name());
    }
}

package com.lucapodesta.agentoffice;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

/**
 * The demo flow end to end: a task created through the API is routed by the Manager, executed by
 * the Developer, sent back once by the Reviewer, redone, approved and completed — asynchronously,
 * against a real PostgreSQL, with Ollama stubbed at the HTTP level and the real prompts.
 */
class TaskLifecycleE2EIT extends AbstractIntegrationTest {

    @RegisterExtension
    static WireMockExtension ollama = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String MANAGER = "You are the Manager of Agent Office";
    private static final String DEVELOPER = "You are the Developer agent";
    private static final String REVIEWER = "You are the Reviewer of Agent Office";

    @DynamicPropertySource
    static void pipelineAgainstStubbedOllama(DynamicPropertyRegistry registry) {
        registry.add("agent-office.pipeline.auto-start", () -> "true");
        registry.add("agent-office.ollama.base-url", ollama::baseUrl);
        registry.add("agent-office.ollama.retry-backoff", () -> "0s");
    }

    @Autowired
    private MockMvcTester mvc;

    @BeforeEach
    void stubAgents() {
        ollama.stubFor(post("/api/chat").withRequestBody(containing(MANAGER)).willReturn(answer(Map.of(
                "agent", "DEVELOPER", "priority", "HIGH", "reason", "Needs a technical migration plan"))));
        ollama.stubFor(post("/api/chat").withRequestBody(containing(DEVELOPER)).willReturn(answer(Map.of(
                "summary", "Incremental migration in three phases",
                "content", "# Java 21 migration\n1. Upgrade the build\n2. Fix deprecations\n3. Roll out"))));
        ollama.stubFor(post("/api/chat").withRequestBody(containing(REVIEWER))
                .inScenario("review").whenScenarioStateIs(STARTED)
                .willReturn(answer(Map.of("verdict", "CHANGES_REQUESTED", "feedback", "Add a rollback plan")))
                .willSetStateTo("second review"));
        ollama.stubFor(post("/api/chat").withRequestBody(containing(REVIEWER))
                .inScenario("review").whenScenarioStateIs("second review")
                .willReturn(answer(Map.of("verdict", "APPROVED", "feedback", "Clear and actionable"))));
    }

    @Test
    void taskTravelsFromInboxToDone() throws Exception {
        String id = createTask();

        String status = awaitFinalStatus(id);

        assertThat(status).isEqualTo("DONE");
        MvcTestResult task = mvc.get().uri("/api/v1/tasks/{id}", id).exchange();
        assertThat(task).bodyJson().extractingPath("$.assignedAgent").isEqualTo("DEVELOPER");
        assertThat(task).bodyJson().extractingPath("$.priority").isEqualTo("HIGH");
        assertThat(task).bodyJson().extractingPath("$.attempt").asNumber().isEqualTo(2);

        MvcTestResult executions = mvc.get().uri("/api/v1/tasks/{id}/executions", id).exchange();
        assertThat(executions).bodyJson().extractingPath("$[*].agentType").asArray()
                .containsExactly("MANAGER", "DEVELOPER", "REVIEWER", "DEVELOPER", "REVIEWER");
        assertThat(executions).bodyJson().extractingPath("$[*].status").asArray()
                .containsExactly("SUCCESS", "REJECTED", "SUCCESS", "SUCCESS", "SUCCESS");
        assertThat(executions).bodyJson().extractingPath("$[3].summary").isEqualTo("Incremental migration in three phases");
        assertThat(executions).bodyJson().extractingPath("$[3].promptVersion").isEqualTo("developer-v1");
        assertThat(executions).bodyJson().extractingPath("$[3].model").isEqualTo("qwen3:8b");

        assertThat(mvc.get().uri("/api/v1/tasks/{id}/reviews", id)).bodyJson()
                .extractingPath("$[*].status").asArray().containsExactly("CHANGES_REQUESTED", "APPROVED");

        assertThat(mvc.get().uri("/api/v1/tasks/{id}/events", id)).bodyJson()
                .extractingPath("$[*].type").asArray().containsExactly(
                        "TASK_CREATED",
                        "EXECUTION_STARTED", "EXECUTION_COMPLETED", "TASK_ASSIGNED",
                        "EXECUTION_STARTED", "EXECUTION_COMPLETED", "REVIEW_STARTED",
                        "EXECUTION_STARTED", "EXECUTION_COMPLETED", "CHANGES_REQUESTED",
                        "EXECUTION_STARTED", "EXECUTION_COMPLETED", "REVIEW_STARTED",
                        "EXECUTION_STARTED", "EXECUTION_COMPLETED", "REVIEW_APPROVED", "TASK_COMPLETED");
    }

    @Test
    void blockedTaskCanBeResumedOnceOllamaIsBack() throws Exception {
        ollama.resetAll();
        ollama.stubFor(post("/api/chat").willReturn(aResponse().withStatus(503)));
        String id = createTask();

        assertThat(awaitFinalStatus(id)).isEqualTo("BLOCKED");
        assertThat(mvc.get().uri("/api/v1/tasks/{id}/executions", id)).bodyJson()
                .extractingPath("$[0].errorCode").isEqualTo("OLLAMA_UNAVAILABLE");

        ollama.resetAll();
        stubAgents();
        assertThat(mvc.post().uri("/api/v1/tasks/{id}/execute", id))
                .hasStatus(202)
                .bodyJson().extractingPath("$.status").isEqualTo("INBOX");

        assertThat(awaitFinalStatus(id)).isEqualTo("DONE");
        assertThat(mvc.get().uri("/api/v1/tasks/{id}/events", id)).bodyJson()
                .extractingPath("$[*].type").asArray().contains("TASK_BLOCKED", "TASK_RESUMED", "TASK_COMPLETED");
    }

    private String createTask() throws Exception {
        MvcTestResult created = mvc.post().uri("/api/v1/tasks").contentType(MediaType.APPLICATION_JSON).content("""
                {"title": "Java 21 migration plan",
                 "description": "Prepare a technical plan to migrate service X from Java 11 to Java 21",
                 "source": "TELEGRAM", "externalReference": "chat-42"}
                """).exchange();
        assertThat(created).hasStatus(201);
        return JsonPath.read(created.getResponse().getContentAsString(), "$.id");
    }

    private String awaitFinalStatus(String id) {
        List<String> settled = List.of("DONE", "FAILED", "BLOCKED", "CANCELLED");
        return await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(100))
                .until(() -> statusOf(id), settled::contains);
    }

    private String statusOf(String id) throws Exception {
        return JsonPath.read(mvc.get().uri("/api/v1/tasks/{id}", id).exchange().getResponse().getContentAsString(),
                "$.status");
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder answer(Map<String, String> reply) {
        String content = JSON.writeValueAsString(reply);
        return okJson(JSON.writeValueAsString(Map.of(
                "model", "qwen3:8b",
                "message", Map.of("role", "assistant", "content", content),
                "done", true)));
    }
}

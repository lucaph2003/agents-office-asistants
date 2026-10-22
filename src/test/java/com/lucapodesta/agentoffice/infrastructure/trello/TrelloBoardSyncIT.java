package com.lucapodesta.agentoffice.infrastructure.trello;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.jayway.jsonpath.JsonPath;
import com.lucapodesta.agentoffice.AbstractIntegrationTest;
import com.lucapodesta.agentoffice.OllamaStubs;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** The board follows a task from creation to Done, and a broken board never breaks the task. */
class TrelloBoardSyncIT extends AbstractIntegrationTest {

    @RegisterExtension
    static WireMockExtension ollama = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    @RegisterExtension
    static WireMockExtension trello = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    @DynamicPropertySource
    static void withTrello(DynamicPropertyRegistry registry) {
        registry.add("agent-office.pipeline.auto-start", () -> "true");
        registry.add("agent-office.ollama.base-url", ollama::baseUrl);
        registry.add("agent-office.trello.enabled", () -> "true");
        registry.add("agent-office.trello.base-url", trello::baseUrl);
        registry.add("agent-office.trello.api-key", () -> "test-key");
        registry.add("agent-office.trello.token", () -> "test-token");
        registry.add("agent-office.trello.board-id", () -> "board-1");
        for (String list : List.of("inbox", "planned", "in-progress", "review", "blocked", "done")) {
            registry.add("agent-office.trello.lists." + list, () -> "list-" + list);
        }
    }

    @Autowired
    private MockMvcTester mvc;

    @BeforeEach
    void stubs() {
        OllamaStubs.happyPath(ollama);
        trello.stubFor(post("/1/cards").willReturn(okJson("""
                {"id": "card-1", "url": "https://trello.com/c/card-1/long", "shortUrl": "https://trello.com/c/card-1"}
                """)));
        trello.stubFor(put("/1/cards/card-1").willReturn(ok()));
        trello.stubFor(post("/1/cards/card-1/actions/comments").willReturn(okJson("{}")));
    }

    @Test
    void cardFollowsTheTaskAcrossTheBoard() throws Exception {
        MvcTestResult created = create();

        assertThat(created).bodyJson().extractingPath("$.boardUrl").isEqualTo("https://trello.com/c/card-1");
        String id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        awaitStatus(id, "DONE");

        assertThat(trello.findAll(postRequestedFor(
                urlEqualTo("/1/cards"))))
                .singleElement()
                .satisfies(request -> assertThat(request.getBodyAsString()).contains("\"idList\":\"list-inbox\""));
        assertThat(columnsVisited()).containsSubsequence("list-planned", "list-in-progress", "list-review", "list-done")
                .endsWith("list-done");
        assertThat(comments()).containsSubsequence(
                "🧭 Manager: Assigned to DEVELOPER (HIGH): Needs a technical migration plan",
                "✅ Developer finished: Incremental migration in three phases",
                "👍 Reviewer approved: Clear and actionable",
                "🏁 Completed (agent=DEVELOPER attempts=1)");
        assertThat(jdbc.queryForObject("SELECT synced_status FROM task_board_cards WHERE task_id = ?::uuid",
                String.class, id)).isEqualTo("DONE");
        assertThat(mvc.get().uri("/api/v1/tasks/{id}/events", id)).bodyJson()
                .extractingPath("$[?(@.type == 'TRELLO_UPDATED')].details").asArray()
                .containsExactly("Card created in INBOX", "Card moved to PLANNED", "Card moved to IN_PROGRESS",
                        "Card moved to REVIEW", "Card moved to DONE");
        assertThat(trello.getAllServeEvents()).allSatisfy(event -> assertThat(event.getRequest().getUrl())
                .doesNotContain("test-token").doesNotContain("test-key"));
    }

    @Test
    void unavailableBoardDoesNotStopTheAgents() throws Exception {
        trello.resetAll();
        trello.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(503)));

        MvcTestResult created = create();
        String id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        awaitStatus(id, "DONE");
        assertThat(created).bodyJson().extractingPath("$.boardUrl").isNull();
        assertThat(mvc.get().uri("/api/v1/tasks/{id}/events", id)).bodyJson()
                .extractingPath("$[?(@.type == 'TRELLO_SYNC_FAILED')].details").asArray()
                .isNotEmpty()
                .allSatisfy(details -> assertThat(details.toString()).contains("Trello create card failed with status 503"));
    }

    private MvcTestResult create() {
        MvcTestResult created = mvc.post().uri("/api/v1/tasks").contentType(MediaType.APPLICATION_JSON).content("""
                {"title": "Java 21 migration plan", "description": "Prepare a technical plan to migrate service X"}
                """).exchange();
        assertThat(created).hasStatus(201);
        return created;
    }

    private void awaitStatus(String id, String expected) {
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(100)).until(() -> JsonPath.read(
                mvc.get().uri("/api/v1/tasks/{id}", id).exchange().getResponse().getContentAsString(), "$.status"),
                expected::equals);
    }

    private List<String> columnsVisited() {
        return trello.findAll(putRequestedFor(
                        urlEqualTo("/1/cards/card-1"))).stream()
                .map(LoggedRequest::getBodyAsString)
                .map(body -> JsonPath.<String>read(body, "$.idList"))
                .toList();
    }

    private List<String> comments() {
        return trello.findAll(postRequestedFor(
                        urlEqualTo("/1/cards/card-1/actions/comments"))).stream()
                .map(request -> JsonPath.<String>read(request.getBodyAsString(), "$.text"))
                .toList();
    }
}

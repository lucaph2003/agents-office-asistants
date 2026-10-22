package com.lucapodesta.agentoffice.infrastructure.notification;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
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

/** A Telegram task comes back to its chat through n8n once the agents are done. */
class NotificationIT extends AbstractIntegrationTest {

    private static final String WEBHOOK = "/webhook/agent-office/task-completed";

    @RegisterExtension
    static WireMockExtension ollama = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    @RegisterExtension
    static WireMockExtension n8n = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    @DynamicPropertySource
    static void withNotifications(DynamicPropertyRegistry registry) {
        registry.add("agent-office.pipeline.auto-start", () -> "true");
        registry.add("agent-office.ollama.base-url", ollama::baseUrl);
        registry.add("agent-office.notifications.n8n.enabled", () -> "true");
        registry.add("agent-office.notifications.n8n.webhook-url", () -> n8n.baseUrl() + WEBHOOK);
        registry.add("agent-office.notifications.n8n.token", () -> "webhook-secret");
    }

    @Autowired
    private MockMvcTester mvc;

    @BeforeEach
    void stubs() {
        OllamaStubs.happyPath(ollama);
        n8n.stubFor(post(WEBHOOK).willReturn(ok()));
    }

    @Test
    void completedTaskIsSentBackToItsChat() throws Exception {
        String id = createTelegramTask();

        await().atMost(Duration.ofSeconds(20)).until(() -> eventTypes(id).contains("USER_NOTIFIED"));

        n8n.verify(1, postRequestedFor(urlEqualTo(WEBHOOK))
                .withHeader("X-Agent-Office-Token", equalTo("webhook-secret"))
                .withHeader("X-Correlation-Id", equalTo("telegram-update-77"))
                .withRequestBody(matchingJsonPath("$.event", equalTo("TASK_COMPLETED")))
                .withRequestBody(matchingJsonPath("$.taskId", equalTo(id)))
                .withRequestBody(matchingJsonPath("$.externalReference", equalTo("chat-42")))
                .withRequestBody(matchingJsonPath("$.summary", equalTo("Incremental migration in three phases")))
                .withRequestBody(matchingJsonPath("$.review", equalTo("APPROVED"))));
    }

    @Test
    void failedDeliveryIsAuditedAndTheTaskStillCompletes() throws Exception {
        n8n.stubFor(post(WEBHOOK).willReturn(aResponse().withStatus(500)));
        String id = createTelegramTask();

        await().atMost(Duration.ofSeconds(20)).until(() -> eventTypes(id).contains("NOTIFICATION_FAILED"));

        assertThat(eventTypes(id)).contains("TASK_COMPLETED").doesNotContain("USER_NOTIFIED");
    }

    private String createTelegramTask() throws Exception {
        String body = mvc.post().uri("/api/v1/tasks").contentType(MediaType.APPLICATION_JSON)
                .header("X-Correlation-Id", "telegram-update-77")
                .content("""
                        {"title": "Java 21 plan", "description": "Prepare a technical plan",
                         "source": "TELEGRAM", "externalReference": "chat-42"}
                        """)
                .exchange().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private List<String> eventTypes(String id) throws Exception {
        return JsonPath.read(mvc.get().uri("/api/v1/tasks/{id}/events", id).exchange().getResponse()
                .getContentAsString(), "$[*].type");
    }
}

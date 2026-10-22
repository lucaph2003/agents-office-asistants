package com.lucapodesta.agentoffice.infrastructure.notification;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.lucapodesta.agentoffice.application.port.out.TaskNotification;
import com.lucapodesta.agentoffice.domain.agent.AgentType;
import com.lucapodesta.agentoffice.domain.exception.ErrorCode;
import com.lucapodesta.agentoffice.domain.exception.NotificationException;
import com.lucapodesta.agentoffice.domain.review.ReviewStatus;
import com.lucapodesta.agentoffice.domain.task.TaskEventType;
import com.lucapodesta.agentoffice.domain.task.TaskPriority;
import com.lucapodesta.agentoffice.domain.task.TaskSource;
import com.lucapodesta.agentoffice.domain.task.TaskStatus;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

class N8nNotificationAdapterTest {

    @RegisterExtension
    static WireMockExtension n8n = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private static final UUID TASK_ID = UUID.fromString("4f0c2a1e-0000-4000-8000-000000000001");
    private static final TaskNotification NOTIFICATION = new TaskNotification(TaskEventType.TASK_COMPLETED, TASK_ID,
            "Java 21 plan", TaskStatus.DONE, TaskPriority.HIGH, AgentType.DEVELOPER, 1, ReviewStatus.APPROVED,
            "Three-phase plan", null, "https://trello.com/c/c1", TaskSource.TELEGRAM, "chat-42");

    @Test
    void postsTheNotificationWithItsToken() {
        n8n.stubFor(post("/webhook/agent-office/task-completed").willReturn(ok()));

        adapter().notify(NOTIFICATION);

        n8n.verify(postRequestedFor(urlEqualTo("/webhook/agent-office/task-completed"))
                .withHeader("X-Agent-Office-Token", equalTo("webhook-secret"))
                .withRequestBody(equalToJson("""
                        {"event": "TASK_COMPLETED", "taskId": "4f0c2a1e-0000-4000-8000-000000000001",
                         "title": "Java 21 plan", "status": "DONE", "priority": "HIGH", "agent": "DEVELOPER",
                         "attempts": 1, "review": "APPROVED", "summary": "Three-phase plan", "reason": null,
                         "boardUrl": "https://trello.com/c/c1", "source": "TELEGRAM", "externalReference": "chat-42"}
                        """)));
    }

    @Test
    void rejectedOrUnreachableWebhookIsReported() {
        n8n.stubFor(post("/webhook/agent-office/task-completed").willReturn(aResponse().withStatus(403)));

        assertThatThrownBy(() -> adapter().notify(NOTIFICATION))
                .isInstanceOf(NotificationException.class)
                .hasMessage("n8n webhook answered with status 403")
                .extracting("code").isEqualTo(ErrorCode.TELEGRAM_NOTIFICATION_FAILED);
        assertThatThrownBy(() -> adapter(URI.create("http://localhost:1/webhook")).notify(NOTIFICATION))
                .hasMessageStartingWith("n8n webhook is unreachable");
    }

    private static N8nNotificationAdapter adapter() {
        return adapter(URI.create(n8n.baseUrl() + "/webhook/agent-office/task-completed"));
    }

    private static N8nNotificationAdapter adapter(URI url) {
        N8nNotificationProperties properties = new N8nNotificationProperties(true, url, "webhook-secret",
                Duration.ofMillis(500), Duration.ofMillis(500));
        return new NotificationConfig().n8nNotificationAdapter(RestClient.builder(), properties);
    }
}

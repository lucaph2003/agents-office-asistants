package com.lucapodesta.agentoffice.infrastructure.notification;

import com.lucapodesta.agentoffice.application.port.out.NotificationPort;
import com.lucapodesta.agentoffice.application.port.out.TaskNotification;
import com.lucapodesta.agentoffice.domain.exception.NotificationException;
import java.net.URI;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Posts notifications to an n8n webhook, which forwards them to the user (Telegram in V1). */
class N8nNotificationAdapter implements NotificationPort {

    static final String TOKEN_HEADER = "X-Agent-Office-Token";

    private final RestClient restClient;
    private final URI webhookUrl;

    N8nNotificationAdapter(RestClient restClient, URI webhookUrl) {
        this.restClient = restClient;
        this.webhookUrl = webhookUrl;
    }

    @Override
    public void notify(TaskNotification notification) {
        try {
            restClient.post()
                    .uri(webhookUrl)
                    .body(notification)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw new NotificationException("n8n webhook answered with status " + ex.getStatusCode().value(), ex);
        } catch (RestClientException ex) {
            throw new NotificationException("n8n webhook is unreachable: " + ex.getClass().getSimpleName(), ex);
        }
    }
}

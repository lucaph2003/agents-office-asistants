package com.lucapodesta.agentoffice.infrastructure.notification;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param webhookUrl n8n webhook of the task-completed-notification workflow
 * @param token      shared secret checked by the webhook's Header Auth credential
 */
@ConfigurationProperties("agent-office.notifications.n8n")
public record N8nNotificationProperties(
        boolean enabled,
        URI webhookUrl,
        String token,
        Duration connectTimeout,
        Duration readTimeout) {

    void requireComplete() {
        if (webhookUrl == null || webhookUrl.toString().isBlank() || token == null || token.isBlank()) {
            throw new IllegalStateException(
                    "n8n notifications are enabled but N8N_TASK_COMPLETED_WEBHOOK_URL or N8N_WEBHOOK_TOKEN is missing");
        }
    }

    @Override
    public String toString() {
        return "N8nNotificationProperties[enabled=%s, webhookUrl=%s, token=***]".formatted(enabled, webhookUrl);
    }
}

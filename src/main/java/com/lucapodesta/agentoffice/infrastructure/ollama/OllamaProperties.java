package com.lucapodesta.agentoffice.infrastructure.ollama;

import com.lucapodesta.agentoffice.domain.agent.AgentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param maxAttempts  total attempts for transient failures (connection refused, 5xx). Timeouts are not retried.
 * @param think        whether thinking-capable models (e.g. qwen3) should reason before answering; slower but sometimes better
 */
@Validated
@ConfigurationProperties("agent-office.ollama")
public record OllamaProperties(
        @NotNull URI baseUrl,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        @Min(1) int maxAttempts,
        @NotNull Duration retryBackoff,
        double temperature,
        boolean think,
        @Valid @NotNull Models models) {

    public record Models(
            @NotBlank String manager,
            @NotBlank String research,
            @NotBlank String developer,
            @NotBlank String reviewer) {

        public String forAgent(AgentType agent) {
            return switch (agent) {
                case MANAGER -> manager;
                case RESEARCH -> research;
                case DEVELOPER -> developer;
                case REVIEWER -> reviewer;
            };
        }
    }
}

package com.lucapodesta.agentoffice.infrastructure.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param autoStart         run the agents as soon as a task is created; otherwise wait for {@code /execute}
 * @param maxAttempts       worker attempts per task before the review loop gives up
 * @param maxOutputAttempts calls per step when the model answers in an unusable format
 */
@Validated
@ConfigurationProperties("agent-office.pipeline")
public record PipelineProperties(
        boolean autoStart,
        @Min(1) @Max(10) int maxAttempts,
        @Min(1) @Max(5) int maxOutputAttempts) {
}

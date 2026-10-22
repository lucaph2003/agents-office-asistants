package com.lucapodesta.agentoffice.application.port.out;

import java.time.Duration;

/**
 * @param promptTokens and {@code outputTokens} are null when the provider does not report them
 */
public record LlmResponse(String content, String model, Duration duration, Integer promptTokens, Integer outputTokens) {
}

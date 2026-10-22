package com.lucapodesta.agentoffice.infrastructure.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Shared secret that n8n (or any client) sends in {@value ApiKeyAuthenticationFilter#HEADER}. */
@Validated
@ConfigurationProperties("agent-office.security")
public record ApiKeyProperties(@NotBlank @Size(min = 16) String apiKey) {

    @Override
    public String toString() {
        return "ApiKeyProperties[apiKey=***]";
    }
}

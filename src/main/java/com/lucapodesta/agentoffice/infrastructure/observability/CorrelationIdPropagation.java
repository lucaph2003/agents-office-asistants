package com.lucapodesta.agentoffice.infrastructure.observability;

import org.slf4j.MDC;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Adds the current correlation id to every outgoing call made with a Spring-built RestClient
 * (Ollama, Trello, n8n), so one request can be followed across systems.
 */
@Component
class CorrelationIdPropagation implements RestClientCustomizer {

    @Override
    public void customize(RestClient.Builder builder) {
        builder.requestInterceptor((request, body, execution) -> {
            String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
            if (correlationId != null && !request.getHeaders().containsHeader(CorrelationIdFilter.HEADER)) {
                request.getHeaders().set(CorrelationIdFilter.HEADER, correlationId);
            }
            return execution.execute(request, body);
        });
    }
}

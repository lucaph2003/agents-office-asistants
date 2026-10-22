package com.lucapodesta.agentoffice.infrastructure.observability;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.MDC;
import org.springframework.web.client.RestClient;

class CorrelationIdPropagationTest {

    @RegisterExtension
    static WireMockExtension server = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private final RestClient client = client();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void forwardsTheCurrentCorrelationId() {
        server.stubFor(get("/ping").willReturn(ok()));
        MDC.put(CorrelationIdFilter.MDC_KEY, "corr-123");

        client.get().uri("/ping").retrieve().toBodilessEntity();

        server.verify(getRequestedFor(urlEqualTo("/ping")).withHeader(CorrelationIdFilter.HEADER, equalTo("corr-123")));
    }

    @Test
    void sendsNothingOutsideARequest() {
        server.stubFor(get("/ping").willReturn(ok()));

        client.get().uri("/ping").retrieve().toBodilessEntity();

        server.verify(getRequestedFor(urlEqualTo("/ping")).withHeader(CorrelationIdFilter.HEADER, absent()));
    }

    @Test
    void keepsAnExplicitHeader() {
        server.stubFor(get("/ping").willReturn(ok()));
        MDC.put(CorrelationIdFilter.MDC_KEY, "corr-123");

        client.get().uri("/ping").header(CorrelationIdFilter.HEADER, "explicit").retrieve().toBodilessEntity();

        server.verify(getRequestedFor(urlEqualTo("/ping")).withHeader(CorrelationIdFilter.HEADER, equalTo("explicit")));
    }

    private static RestClient client() {
        RestClient.Builder builder = RestClient.builder().baseUrl(server.baseUrl());
        new CorrelationIdPropagation().customize(builder);
        return builder.build();
    }
}

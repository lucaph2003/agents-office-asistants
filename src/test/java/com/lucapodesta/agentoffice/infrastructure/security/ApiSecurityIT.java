package com.lucapodesta.agentoffice.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.lucapodesta.agentoffice.AbstractIntegrationTest;
import com.lucapodesta.agentoffice.infrastructure.observability.CorrelationIdFilter;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ApiSecurityIT extends AbstractIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    /** MockMvc without the default API key header, with filters in the same order as the server. */
    private MockMvcTester anonymous() {
        return MockMvcTester.create(MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean(CorrelationIdFilter.class),
                        context.getBean("springSecurityFilterChain", Filter.class))
                .build());
    }

    @Test
    void taskApiRequiresTheApiKey() {
        MvcTestResult result = anonymous().get().uri("/api/v1/tasks").header("X-Correlation-Id", "no-key-1").exchange();

        assertThat(result).hasStatus(401);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("UNAUTHORIZED");
        assertThat(result).bodyJson().extractingPath("$.correlationId").isEqualTo("no-key-1");
    }

    @Test
    void wrongApiKeyIsRejected() {
        assertThat(anonymous().get().uri("/api/v1/tasks").header("X-Api-Key", "not-the-right-key-at-all"))
                .hasStatus(401);
    }

    @Test
    void healthAndDocsStayPublic() {
        assertThat(anonymous().get().uri("/actuator/health")).hasStatusOk();
        assertThat(anonymous().get().uri("/v3/api-docs")).hasStatusOk()
                .bodyJson().extractingPath("$.components.securitySchemes.apiKey.name").isEqualTo("X-Api-Key");
    }

    @Test
    void metricsNeedTheApiKey() {
        assertThat(anonymous().get().uri("/actuator/metrics")).hasStatus(401);
        assertThat(anonymous().get().uri("/actuator/metrics").header("X-Api-Key", "test-api-key-0123456789"))
                .hasStatusOk();
    }

    @Test
    void validKeyIsAccepted() {
        assertThat(anonymous().get().uri("/api/v1/tasks").header("X-Api-Key", "test-api-key-0123456789"))
                .hasStatusOk();
    }
}

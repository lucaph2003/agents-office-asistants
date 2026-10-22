package com.lucapodesta.agentoffice;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

class AgentOfficeApplicationIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void healthEndpointReportsUp() {
        assertThat(mvc.get().uri("/actuator/health"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("UP");
    }

    @Test
    void openApiDocumentDescribesTaskEndpoints() {
        assertThat(mvc.get().uri("/v3/api-docs"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.paths")
                .asMap()
                .containsKeys("/api/v1/tasks", "/api/v1/tasks/{id}", "/api/v1/tasks/{id}/cancel", "/api/v1/tasks/{id}/events");
    }
}

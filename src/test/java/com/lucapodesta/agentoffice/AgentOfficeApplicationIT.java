package com.lucapodesta.agentoffice;

import static org.assertj.core.api.Assertions.assertThat;

import com.lucapodesta.agentoffice.application.port.out.LanguageModelPort;
import com.lucapodesta.agentoffice.infrastructure.ollama.OllamaLanguageModelAdapter;
import com.lucapodesta.agentoffice.infrastructure.ollama.OllamaProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

class AgentOfficeApplicationIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private LanguageModelPort languageModel;

    @Autowired
    private OllamaProperties ollamaProperties;

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

    @Test
    void ollamaIsWiredAsTheLanguageModel() {
        assertThat(languageModel).isInstanceOf(OllamaLanguageModelAdapter.class);
        assertThat(ollamaProperties.models().developer()).isEqualTo("qwen3:8b");
        assertThat(ollamaProperties.baseUrl()).hasToString("http://localhost:11434");
    }
}

package com.lucapodesta.agentoffice.infrastructure.ollama;

import static org.assertj.core.api.Assertions.assertThat;

import com.lucapodesta.agentoffice.application.port.out.LlmRequest;
import com.lucapodesta.agentoffice.application.port.out.LlmResponse;
import com.lucapodesta.agentoffice.domain.agent.AgentType;
import com.lucapodesta.agentoffice.infrastructure.prompt.ClasspathPromptCatalog;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Talks to a real local Ollama with the real prompts. Opt-in, never part of CI:
 * {@code OLLAMA_SMOKE_TEST=true ./mvnw verify -Dit.test=OllamaSmokeIT}
 */
@EnabledIfEnvironmentVariable(named = "OLLAMA_SMOKE_TEST", matches = "true")
class OllamaSmokeIT {

    private static final Logger log = LoggerFactory.getLogger(OllamaSmokeIT.class);

    private static final String TASK = """
            <task>
            Title: Java 21 migration
            Description: Analyze whether we should migrate a Spring Boot 2 service running on Java 11 to Java 21.
            </task>
            """;

    private final ClasspathPromptCatalog prompts = new ClasspathPromptCatalog();
    private final OllamaLanguageModelAdapter adapter = adapter();

    @Test
    void managerReturnsARoutingDecision() {
        Map<?, ?> decision = askJson(AgentType.MANAGER);

        assertThat(decision.get("agent")).isIn("RESEARCH", "DEVELOPER");
        assertThat(decision.get("priority")).isIn("LOW", "MEDIUM", "HIGH", "URGENT");
        assertThat((String) decision.get("reason")).isNotBlank();
    }

    @Test
    void developerReturnsSummaryAndContent() {
        Map<?, ?> deliverable = askJson(AgentType.DEVELOPER);

        assertThat((String) deliverable.get("summary")).isNotBlank();
        assertThat((String) deliverable.get("content")).isNotBlank();
    }

    private Map<?, ?> askJson(AgentType agent) {
        LlmResponse response = adapter.generate(
                new LlmRequest(agent, prompts.systemPrompt(agent).text(), TASK, true));
        log.info("{} via {} in {} ({}/{} tokens):\n{}", agent, response.model(), response.duration(),
                response.promptTokens(), response.outputTokens(), response.content());
        return JsonMapper.builder().build().readValue(response.content(), Map.class);
    }

    private static OllamaLanguageModelAdapter adapter() {
        String model = System.getenv().getOrDefault("OLLAMA_MODEL", "qwen3:8b");
        OllamaProperties properties = new OllamaProperties(
                URI.create(System.getenv().getOrDefault("OLLAMA_BASE_URL", "http://localhost:11434")),
                Duration.ofSeconds(5), Duration.ofMinutes(5), 1, Duration.ZERO, 0.2, false,
                new OllamaProperties.Models(model, model, model, model));
        return new OllamaLanguageModelAdapter(OllamaConfig.ollamaClient(RestClient.builder(), properties), properties);
    }
}

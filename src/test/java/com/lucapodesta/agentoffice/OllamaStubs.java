package com.lucapodesta.agentoffice;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/** Stubs Ollama's chat API per agent, recognising each agent by its real system prompt. */
public final class OllamaStubs {

    public static final String MANAGER = "You are the Manager of Agent Office";
    public static final String DEVELOPER = "You are the Developer agent";
    public static final String REVIEWER = "You are the Reviewer of Agent Office";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private OllamaStubs() {
    }

    /** Manager routes to the Developer, the Developer delivers, the Reviewer approves. */
    public static void happyPath(WireMockExtension ollama) {
        ollama.stubFor(agent(MANAGER).willReturn(answer(Map.of(
                "agent", "DEVELOPER", "priority", "HIGH", "reason", "Needs a technical migration plan"))));
        ollama.stubFor(agent(DEVELOPER).willReturn(answer(Map.of(
                "summary", "Incremental migration in three phases",
                "content", "# Java 21 migration\n1. Upgrade the build\n2. Fix deprecations\n3. Roll out"))));
        ollama.stubFor(agent(REVIEWER).willReturn(answer(Map.of("verdict", "APPROVED", "feedback", "Clear and actionable"))));
    }

    public static MappingBuilder agent(String systemPromptMarker) {
        return post("/api/chat").withRequestBody(containing(systemPromptMarker));
    }

    public static ResponseDefinitionBuilder answer(Map<String, String> reply) {
        String content = JSON.writeValueAsString(reply);
        return okJson(JSON.writeValueAsString(Map.of(
                "model", "qwen3:8b",
                "message", Map.of("role", "assistant", "content", content),
                "done", true)));
    }
}

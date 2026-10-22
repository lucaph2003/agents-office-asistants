package com.lucapodesta.agentoffice.infrastructure.ollama;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.lucapodesta.agentoffice.application.port.out.LlmRequest;
import com.lucapodesta.agentoffice.application.port.out.LlmResponse;
import com.lucapodesta.agentoffice.domain.agent.AgentType;
import com.lucapodesta.agentoffice.domain.exception.ErrorCode;
import com.lucapodesta.agentoffice.domain.exception.LanguageModelException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

class OllamaLanguageModelAdapterTest {

    @RegisterExtension
    static WireMockExtension ollama = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

    private static final LlmRequest JSON_REQUEST =
            new LlmRequest(AgentType.DEVELOPER, "You are a developer", "<task>Plan</task>", true);

    @Test
    void sendsChatRequestForTheAgentModelAndMapsTheAnswer() {
        ollama.stubFor(post("/api/chat").willReturn(okJson("""
                {"model": "dev-model", "message": {"role": "assistant", "content": "{\\"summary\\": \\"ok\\"}"},
                 "done": true, "done_reason": "stop", "total_duration": 1500000000,
                 "prompt_eval_count": 42, "eval_count": 7, "unexpected_field": 1}
                """)));

        LlmResponse response = adapter(3).generate(JSON_REQUEST);

        assertThat(response.content()).isEqualTo("{\"summary\": \"ok\"}");
        assertThat(response.model()).isEqualTo("dev-model");
        assertThat(response.duration()).isEqualTo(Duration.ofMillis(1500));
        assertThat(response.promptTokens()).isEqualTo(42);
        assertThat(response.outputTokens()).isEqualTo(7);
        ollama.verify(postRequestedFor(urlEqualTo("/api/chat")).withRequestBody(equalToJson("""
                {"model": "dev-model",
                 "messages": [{"role": "system", "content": "You are a developer"},
                              {"role": "user", "content": "<task>Plan</task>"}],
                 "stream": false, "format": "json", "think": false,
                 "options": {"temperature": 0.2}}
                """)));
    }

    @Test
    void recordsCallDurationAndTokensPerAgentAndModel() {
        ollama.stubFor(post("/api/chat").willReturn(okJson("""
                {"model": "dev-model", "message": {"role": "assistant", "content": "ok"}, "done": true,
                 "prompt_eval_count": 42, "eval_count": 7}
                """)));

        adapter(1).generate(JSON_REQUEST);

        assertThat(meters.get("agent_office.llm.calls")
                .tags("agent", "DEVELOPER", "model", "dev-model", "outcome", "success").timer().count()).isEqualTo(1);
        assertThat(meters.get("agent_office.llm.tokens").tags("direction", "prompt").summary().totalAmount()).isEqualTo(42);
        assertThat(meters.get("agent_office.llm.tokens").tags("direction", "output").summary().totalAmount()).isEqualTo(7);
    }

    @Test
    void recordsFailuresByErrorCode() {
        ollama.stubFor(post("/api/chat").willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> adapter(1).generate(JSON_REQUEST)).isInstanceOf(LanguageModelException.class);

        assertThat(meters.get("agent_office.llm.calls").tags("outcome", "AGENT_NOT_AVAILABLE").timer().count())
                .isEqualTo(1);
    }

    @Test
    void omitsFormatForFreeTextAndUsesPerAgentModel() {
        ollama.stubFor(post("/api/chat").willReturn(okJson(answer("Plain text"))));

        adapter(1).generate(new LlmRequest(AgentType.REVIEWER, "system", "user", false));

        ollama.verify(postRequestedFor(urlEqualTo("/api/chat"))
                .withRequestBody(equalToJson("{\"model\": \"review-model\", \"stream\": false}", true, true)));
        assertThat(ollama.getAllServeEvents().getFirst().getRequest().getBodyAsString()).doesNotContain("format");
    }

    @Test
    void stripsInlineReasoning() {
        ollama.stubFor(post("/api/chat").willReturn(okJson(answer("<think>\\nhmm\\n</think>\\n\\nFinal answer"))));

        assertThat(adapter(1).generate(JSON_REQUEST).content()).isEqualTo("Final answer");
    }

    @Test
    void retriesTransientServerErrors() {
        ollama.stubFor(post("/api/chat").inScenario("flaky").whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(503)).willSetStateTo("recovered"));
        ollama.stubFor(post("/api/chat").inScenario("flaky").whenScenarioStateIs("recovered")
                .willReturn(okJson(answer("recovered"))));

        assertThat(adapter(2).generate(JSON_REQUEST).content()).isEqualTo("recovered");
        ollama.verify(2, postRequestedFor(urlEqualTo("/api/chat")));
    }

    @Test
    void givesUpAfterMaxAttempts() {
        ollama.stubFor(post("/api/chat").willReturn(aResponse().withStatus(500).withBody("model runner crashed")));

        assertThatThrownBy(() -> adapter(3).generate(JSON_REQUEST))
                .isInstanceOf(LanguageModelException.class)
                .hasMessageContaining("after 3 attempt(s): status 500")
                .extracting("code").isEqualTo(ErrorCode.OLLAMA_UNAVAILABLE);
        ollama.verify(3, postRequestedFor(urlEqualTo("/api/chat")));
    }

    @Test
    void doesNotRetryTimeouts() {
        ollama.stubFor(post("/api/chat").willReturn(okJson(answer("late")).withFixedDelay(1_000)));

        assertThatThrownBy(() -> adapter(3).generate(JSON_REQUEST))
                .isInstanceOf(LanguageModelException.class)
                .hasMessageContaining("did not answer in time")
                .extracting("code").isEqualTo(ErrorCode.OLLAMA_UNAVAILABLE);
        ollama.verify(1, postRequestedFor(urlEqualTo("/api/chat")));
    }

    @Test
    void missingModelIsReportedAsAgentNotAvailable() {
        ollama.stubFor(post("/api/chat").willReturn(aResponse().withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\": \"model 'dev-model' not found\"}")));

        assertThatThrownBy(() -> adapter(3).generate(JSON_REQUEST))
                .isInstanceOf(LanguageModelException.class)
                .hasMessageContaining("ollama pull dev-model")
                .extracting("code").isEqualTo(ErrorCode.AGENT_NOT_AVAILABLE);
        ollama.verify(1, postRequestedFor(urlEqualTo("/api/chat")));
    }

    @Test
    void otherClientErrorsAreNotRetried() {
        ollama.stubFor(post("/api/chat").willReturn(aResponse().withStatus(400)));

        assertThatThrownBy(() -> adapter(3).generate(JSON_REQUEST))
                .hasMessageContaining("status 400")
                .extracting("code").isEqualTo(ErrorCode.OLLAMA_UNAVAILABLE);
        ollama.verify(1, postRequestedFor(urlEqualTo("/api/chat")));
    }

    @Test
    void rejectsUnreadableBody() {
        ollama.stubFor(post("/api/chat").willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json").withBody("{not json")));

        assertThatThrownBy(() -> adapter(1).generate(JSON_REQUEST))
                .hasMessageContaining("could not be read")
                .extracting("code").isEqualTo(ErrorCode.OLLAMA_UNAVAILABLE);
    }

    @Test
    void rejectsEmptyAnswer() {
        ollama.stubFor(post("/api/chat").willReturn(okJson(answer("<think>only thoughts</think>  "))));

        assertThatThrownBy(() -> adapter(1).generate(JSON_REQUEST))
                .hasMessageContaining("empty answer")
                .extracting("code").isEqualTo(ErrorCode.OLLAMA_UNAVAILABLE);
    }

    @Test
    void unreachableServerIsRetriedThenReported() {
        OllamaLanguageModelAdapter unreachable = adapter(URI.create("http://localhost:1"), 2);

        assertThatThrownBy(() -> unreachable.generate(JSON_REQUEST))
                .hasMessageContaining("after 2 attempt(s)")
                .extracting("code").isEqualTo(ErrorCode.OLLAMA_UNAVAILABLE);
    }

    private OllamaLanguageModelAdapter adapter(int maxAttempts) {
        return adapter(URI.create(ollama.baseUrl()), maxAttempts);
    }

    private OllamaLanguageModelAdapter adapter(URI baseUrl, int maxAttempts) {
        OllamaProperties properties = new OllamaProperties(baseUrl, Duration.ofMillis(500), Duration.ofMillis(300),
                maxAttempts, Duration.ZERO, 0.2, false,
                new OllamaProperties.Models("manager-model", "research-model", "dev-model", "review-model"));
        return new OllamaLanguageModelAdapter(OllamaConfig.ollamaClient(RestClient.builder(), properties), properties,
                meters);
    }

    private static String answer(String content) {
        return """
                {"model": "dev-model", "message": {"role": "assistant", "content": "%s"}, "done": true}
                """.formatted(content);
    }
}

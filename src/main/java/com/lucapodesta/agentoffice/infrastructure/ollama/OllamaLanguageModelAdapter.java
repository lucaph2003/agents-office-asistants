package com.lucapodesta.agentoffice.infrastructure.ollama;

import com.lucapodesta.agentoffice.application.port.out.LanguageModelPort;
import com.lucapodesta.agentoffice.application.port.out.LlmRequest;
import com.lucapodesta.agentoffice.application.port.out.LlmResponse;
import com.lucapodesta.agentoffice.domain.agent.AgentType;
import com.lucapodesta.agentoffice.domain.exception.ErrorCode;
import com.lucapodesta.agentoffice.domain.exception.LanguageModelException;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OllamaLanguageModelAdapter implements LanguageModelPort {

    private static final Logger log = LoggerFactory.getLogger(OllamaLanguageModelAdapter.class);

    /** Some models inline their reasoning even when asked not to; it never belongs in the answer. */
    private static final Pattern THINK_BLOCK = Pattern.compile("(?s)<think>.*?</think>");

    private final OllamaClient client;
    private final OllamaProperties properties;
    private final MeterRegistry meters;

    OllamaLanguageModelAdapter(OllamaClient client, OllamaProperties properties, MeterRegistry meters) {
        this.client = client;
        this.properties = properties;
        this.meters = meters;
    }

    @Override
    public LlmResponse generate(LlmRequest request) {
        String model = modelFor(request.agent());
        Timer.Sample sample = Timer.start(meters);
        try {
            LlmResponse response = call(request, model);
            sample.stop(callTimer(request, model, "success"));
            recordTokens(request, model, response);
            return response;
        } catch (LanguageModelException ex) {
            sample.stop(callTimer(request, model, ex.code().name()));
            throw ex;
        }
    }

    private LlmResponse call(LlmRequest request, String model) {
        OllamaChatResponse response = client.chat(new OllamaChatRequest(
                model,
                List.of(new OllamaChatRequest.Message("system", request.systemPrompt()),
                        new OllamaChatRequest.Message("user", request.userPrompt())),
                false,
                request.jsonOutput() ? "json" : null,
                properties.think(),
                Map.of("temperature", properties.temperature())));

        String content = contentOf(response, model);
        Duration duration = response.totalDuration() == null ? null : Duration.ofNanos(response.totalDuration());
        if ("length".equals(response.doneReason())) {
            log.warn("event=LLM_OUTPUT_TRUNCATED agent={} model={}", request.agent(), model);
        }
        log.info("event=LLM_CALL_COMPLETED agent={} model={} durationMs={} promptTokens={} outputTokens={}",
                request.agent(), model, duration == null ? null : duration.toMillis(),
                response.promptEvalCount(), response.evalCount());
        return new LlmResponse(content, response.model() != null ? response.model() : model, duration,
                response.promptEvalCount(), response.evalCount());
    }

    private Timer callTimer(LlmRequest request, String model, String outcome) {
        return Timer.builder("agent_office.llm.calls")
                .description("Language model calls")
                .tags("agent", request.agent().name(), "model", model, "outcome", outcome)
                .register(meters);
    }

    private void recordTokens(LlmRequest request, String model, LlmResponse response) {
        record(request, model, "prompt", response.promptTokens());
        record(request, model, "output", response.outputTokens());
    }

    private void record(LlmRequest request, String model, String direction, Integer tokens) {
        if (tokens != null) {
            DistributionSummary.builder("agent_office.llm.tokens")
                    .description("Tokens per language model call")
                    .tags("agent", request.agent().name(), "model", model, "direction", direction)
                    .register(meters)
                    .record(tokens);
        }
    }

    @Override
    public String modelFor(AgentType agent) {
        return properties.models().forAgent(agent);
    }

    private static String contentOf(OllamaChatResponse response, String model) {
        String raw = response == null || response.message() == null ? null : response.message().content();
        String content = raw == null ? "" : THINK_BLOCK.matcher(raw).replaceAll("").strip();
        if (content.isEmpty()) {
            throw new LanguageModelException(ErrorCode.OLLAMA_UNAVAILABLE, "Ollama returned an empty answer (model " + model + ")");
        }
        return content;
    }
}

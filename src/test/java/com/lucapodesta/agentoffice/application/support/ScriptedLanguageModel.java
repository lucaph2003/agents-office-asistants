package com.lucapodesta.agentoffice.application.support;

import com.lucapodesta.agentoffice.application.port.out.LanguageModelPort;
import com.lucapodesta.agentoffice.application.port.out.LlmRequest;
import com.lucapodesta.agentoffice.application.port.out.LlmResponse;
import com.lucapodesta.agentoffice.application.port.out.PromptCatalog;
import com.lucapodesta.agentoffice.domain.agent.AgentType;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Language model whose answers are queued per agent. Records every request it receives. */
public class ScriptedLanguageModel implements LanguageModelPort {

    public static final PromptCatalog PROMPTS =
            agent -> new PromptCatalog.Prompt(agent.name().toLowerCase() + "-test", "You are the " + agent + " agent");

    private final Map<AgentType, Deque<Supplier<String>>> script = new EnumMap<>(AgentType.class);
    private final List<LlmRequest> requests = new ArrayList<>();

    public ScriptedLanguageModel answer(AgentType agent, String... answers) {
        for (String answer : answers) {
            queue(agent).add(() -> answer);
        }
        return this;
    }

    public ScriptedLanguageModel fail(AgentType agent, RuntimeException error) {
        queue(agent).add(() -> {
            throw error;
        });
        return this;
    }

    /** Runs a side effect (e.g. a concurrent cancellation) while the model is "thinking". */
    public ScriptedLanguageModel answerAfter(AgentType agent, Runnable sideEffect, String answer) {
        queue(agent).add(() -> {
            sideEffect.run();
            return answer;
        });
        return this;
    }

    @Override
    public LlmResponse generate(LlmRequest request) {
        requests.add(request);
        Supplier<String> next = queue(request.agent()).poll();
        if (next == null) {
            throw new AssertionError("No scripted answer left for " + request.agent());
        }
        return new LlmResponse(next.get(), modelFor(request.agent()), Duration.ofMillis(5), 10, 20);
    }

    @Override
    public String modelFor(AgentType agent) {
        return "test-model";
    }

    public List<LlmRequest> requestsFor(AgentType agent) {
        return requests.stream().filter(r -> r.agent() == agent).toList();
    }

    private Deque<Supplier<String>> queue(AgentType agent) {
        return script.computeIfAbsent(agent, a -> new ArrayDeque<>());
    }
}

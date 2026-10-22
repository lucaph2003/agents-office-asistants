package com.lucapodesta.agentoffice.application.port.out;

import com.lucapodesta.agentoffice.domain.agent.AgentType;
import java.util.Objects;

/**
 * @param jsonOutput asks the provider to constrain the answer to a JSON object
 */
public record LlmRequest(AgentType agent, String systemPrompt, String userPrompt, boolean jsonOutput) {

    public LlmRequest {
        Objects.requireNonNull(agent, "agent");
        Objects.requireNonNull(systemPrompt, "systemPrompt");
        Objects.requireNonNull(userPrompt, "userPrompt");
    }
}

package com.lucapodesta.agentoffice.application.port.out;

import com.lucapodesta.agentoffice.domain.agent.AgentType;

/**
 * Text generation, independent of the provider. Implementations choose the concrete model
 * for each agent and report it back in the response.
 *
 * @throws com.lucapodesta.agentoffice.domain.exception.LanguageModelException when no usable answer was obtained
 */
public interface LanguageModelPort {

    LlmResponse generate(LlmRequest request);

    /** The model that will serve this agent, recorded on each execution even when the call fails. */
    String modelFor(AgentType agent);
}

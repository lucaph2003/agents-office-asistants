package com.lucapodesta.agentoffice.application.port.out;

/**
 * Text generation, independent of the provider. Implementations choose the concrete model
 * for each agent and report it back in the response.
 *
 * @throws com.lucapodesta.agentoffice.domain.exception.LanguageModelException when no usable answer was obtained
 */
public interface LanguageModelPort {

    LlmResponse generate(LlmRequest request);
}

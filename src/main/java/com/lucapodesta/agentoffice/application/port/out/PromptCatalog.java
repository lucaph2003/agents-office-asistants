package com.lucapodesta.agentoffice.application.port.out;

import com.lucapodesta.agentoffice.domain.agent.AgentType;

public interface PromptCatalog {

    Prompt systemPrompt(AgentType agent);

    /** A system prompt and the version recorded on every execution that used it. */
    record Prompt(String version, String text) {
    }
}

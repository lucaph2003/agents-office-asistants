package com.lucapodesta.agentoffice.domain.agent;

public enum AgentType {
    MANAGER,
    RESEARCH,
    DEVELOPER,
    REVIEWER;

    /** Agents that produce work for a task, as opposed to coordinating or reviewing it. */
    public boolean isWorker() {
        return this == RESEARCH || this == DEVELOPER;
    }
}

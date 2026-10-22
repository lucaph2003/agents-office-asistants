package com.lucapodesta.agentoffice.domain.agent;

import com.lucapodesta.agentoffice.domain.exception.DomainValidationException;
import com.lucapodesta.agentoffice.domain.task.TaskPriority;

/** The Manager's routing verdict for a task: which worker gets it and how urgent it is. */
public record ManagerDecision(AgentType agent, TaskPriority priority, String reason) {

    public ManagerDecision {
        if (agent == null || !agent.isWorker()) {
            throw new DomainValidationException("Manager can only assign a worker agent, got " + agent);
        }
        if (priority == null) {
            throw new DomainValidationException("Manager decision requires a priority");
        }
        if (reason == null || reason.isBlank()) {
            throw new DomainValidationException("Manager decision requires a reason");
        }
    }
}

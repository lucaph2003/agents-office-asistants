package com.lucapodesta.agentoffice.application.agent;

import com.lucapodesta.agentoffice.domain.agent.AgentType;
import java.util.List;

public record AgentTeam(ManagerAgent manager, List<WorkerAgent> workers, ReviewerAgent reviewer) {

    public WorkerAgent worker(AgentType type) {
        return workers.stream()
                .filter(worker -> worker.type() == type)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No worker registered for " + type));
    }
}

package com.lucapodesta.agentoffice.application.port.out;

import com.lucapodesta.agentoffice.domain.execution.AgentExecution;
import java.util.List;
import java.util.UUID;

public interface AgentExecutionRepository {

    AgentExecution save(AgentExecution execution);

    List<AgentExecution> findByTaskId(UUID taskId);
}

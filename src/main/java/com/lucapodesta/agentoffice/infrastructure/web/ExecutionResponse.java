package com.lucapodesta.agentoffice.infrastructure.web;

import com.lucapodesta.agentoffice.domain.agent.AgentType;
import com.lucapodesta.agentoffice.domain.exception.ErrorCode;
import com.lucapodesta.agentoffice.domain.execution.AgentExecution;
import com.lucapodesta.agentoffice.domain.execution.ExecutionStatus;
import java.time.Instant;
import java.util.UUID;

public record ExecutionResponse(
        UUID id,
        AgentType agentType,
        ExecutionStatus status,
        int attempt,
        String model,
        String promptVersion,
        String summary,
        String output,
        ErrorCode errorCode,
        Instant startedAt,
        Instant finishedAt) {

    static ExecutionResponse from(AgentExecution execution) {
        return new ExecutionResponse(execution.id(), execution.agentType(), execution.status(), execution.attempt(),
                execution.model(), execution.promptVersion(), execution.summary(), execution.output(),
                execution.errorCode(), execution.startedAt(), execution.finishedAt());
    }
}

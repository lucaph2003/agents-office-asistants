package com.lucapodesta.agentoffice.infrastructure.web;

import com.lucapodesta.agentoffice.domain.agent.AgentType;
import com.lucapodesta.agentoffice.domain.task.Task;
import com.lucapodesta.agentoffice.domain.task.TaskPriority;
import com.lucapodesta.agentoffice.domain.task.TaskSource;
import com.lucapodesta.agentoffice.domain.task.TaskStatus;
import java.time.Instant;
import java.util.UUID;

public record TaskResponse(
        UUID id,
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        AgentType assignedAgent,
        TaskSource source,
        String externalReference,
        boolean requiresHumanApproval,
        int attempt,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt) {

    static TaskResponse from(Task task) {
        return new TaskResponse(task.id(), task.title(), task.description(), task.status(), task.priority(),
                task.assignedAgent(), task.source(), task.externalReference(), task.requiresHumanApproval(),
                task.attempt(), task.createdAt(), task.updatedAt(), task.completedAt());
    }
}

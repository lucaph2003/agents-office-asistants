package com.lucapodesta.agentoffice.infrastructure.web;

import com.lucapodesta.agentoffice.domain.agent.AgentType;
import com.lucapodesta.agentoffice.domain.task.Task;
import com.lucapodesta.agentoffice.domain.task.TaskPriority;
import com.lucapodesta.agentoffice.domain.task.TaskSource;
import com.lucapodesta.agentoffice.domain.task.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
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
        Instant completedAt,
        @Schema(description = "Link to the task's card, when the board integration is enabled")
        String boardUrl) {

    static TaskResponse from(Task task, String boardUrl) {
        return new TaskResponse(task.id(), task.title(), task.description(), task.status(), task.priority(),
                task.assignedAgent(), task.source(), task.externalReference(), task.requiresHumanApproval(),
                task.attempt(), task.createdAt(), task.updatedAt(), task.completedAt(), boardUrl);
    }
}

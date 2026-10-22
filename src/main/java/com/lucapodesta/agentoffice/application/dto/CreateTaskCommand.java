package com.lucapodesta.agentoffice.application.dto;

import com.lucapodesta.agentoffice.domain.task.TaskPriority;
import com.lucapodesta.agentoffice.domain.task.TaskSource;

public record CreateTaskCommand(
        String title,
        String description,
        TaskPriority priority,
        TaskSource source,
        String externalReference,
        boolean requiresHumanApproval) {
}

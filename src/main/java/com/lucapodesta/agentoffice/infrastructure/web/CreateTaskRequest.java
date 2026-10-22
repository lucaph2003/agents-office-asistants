package com.lucapodesta.agentoffice.infrastructure.web;

import com.lucapodesta.agentoffice.application.dto.CreateTaskCommand;
import com.lucapodesta.agentoffice.domain.task.Task;
import com.lucapodesta.agentoffice.domain.task.TaskPriority;
import com.lucapodesta.agentoffice.domain.task.TaskSource;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTaskRequest(
        @Schema(example = "Prepare Java migration proposal")
        @NotBlank @Size(max = Task.MAX_TITLE_LENGTH) String title,

        @Schema(example = "Create a plan to migrate an application from Java 11 to Java 21")
        @NotBlank @Size(max = Task.MAX_DESCRIPTION_LENGTH) String description,

        @Schema(description = "Initial priority; the Manager may change it. Defaults to MEDIUM")
        TaskPriority priority,

        @Schema(description = "Where the task came from. Defaults to API")
        TaskSource source,

        @Schema(description = "Identifier in the source system, e.g. a Telegram chat id", example = "123456789")
        @Size(max = 255) String externalReference,

        @Schema(description = "Wait for a human approval after the Reviewer agent approves. Defaults to false")
        Boolean requiresHumanApproval) {

    CreateTaskCommand toCommand() {
        return new CreateTaskCommand(title, description, priority, source == null ? TaskSource.API : source,
                externalReference, Boolean.TRUE.equals(requiresHumanApproval));
    }
}

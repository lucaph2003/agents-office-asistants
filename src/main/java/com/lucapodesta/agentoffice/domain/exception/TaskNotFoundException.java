package com.lucapodesta.agentoffice.domain.exception;

import java.util.UUID;

public class TaskNotFoundException extends AgentOfficeException {

    public TaskNotFoundException(UUID taskId) {
        super(ErrorCode.TASK_NOT_FOUND, "Task %s was not found".formatted(taskId));
    }
}

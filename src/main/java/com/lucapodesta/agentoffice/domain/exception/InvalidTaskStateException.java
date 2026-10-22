package com.lucapodesta.agentoffice.domain.exception;

import com.lucapodesta.agentoffice.domain.task.TaskStatus;
import java.util.UUID;

public class InvalidTaskStateException extends AgentOfficeException {

    public InvalidTaskStateException(UUID taskId, TaskStatus from, TaskStatus to) {
        super(ErrorCode.INVALID_TASK_STATE,
                "Task %s cannot move from %s to %s".formatted(taskId, from, to));
    }
}

package com.lucapodesta.agentoffice.application.dto;

import java.util.UUID;

/** Published when someone explicitly asks to run or resume a task. */
public record TaskExecutionRequestedEvent(UUID taskId) {
}

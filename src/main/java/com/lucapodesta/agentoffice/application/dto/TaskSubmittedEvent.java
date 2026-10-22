package com.lucapodesta.agentoffice.application.dto;

import java.util.UUID;

/** Published when a task is created. Whether it starts automatically is a deployment choice. */
public record TaskSubmittedEvent(UUID taskId) {
}

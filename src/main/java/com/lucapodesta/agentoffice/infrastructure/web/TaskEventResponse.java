package com.lucapodesta.agentoffice.infrastructure.web;

import com.lucapodesta.agentoffice.domain.task.TaskEvent;
import com.lucapodesta.agentoffice.domain.task.TaskEventType;
import java.time.Instant;
import java.util.UUID;

public record TaskEventResponse(UUID id, TaskEventType type, String actor, String details, Instant createdAt) {

    static TaskEventResponse from(TaskEvent event) {
        return new TaskEventResponse(event.id(), event.type(), event.actor(), event.details(), event.createdAt());
    }
}

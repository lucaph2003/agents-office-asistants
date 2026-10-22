package com.lucapodesta.agentoffice.domain.task;

import com.lucapodesta.agentoffice.domain.agent.AgentType;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Audit trail entry. Append-only; it records what happened, it does not drive state. */
public record TaskEvent(UUID id, UUID taskId, TaskEventType type, String actor, String details, Instant createdAt) {

    public static final String SYSTEM = "system";
    public static final String USER = "user";
    public static final int MAX_DETAILS_LENGTH = 1_000;

    public TaskEvent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(createdAt, "createdAt");
        if (details != null && details.length() > MAX_DETAILS_LENGTH) {
            details = details.substring(0, MAX_DETAILS_LENGTH);
        }
    }

    public static TaskEvent of(UUID taskId, TaskEventType type, String actor, String details, Instant now) {
        return new TaskEvent(UUID.randomUUID(), taskId, type, actor, details, now);
    }

    public static String agent(AgentType type) {
        return "agent:" + type.name().toLowerCase();
    }
}

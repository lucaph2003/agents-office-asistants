package com.lucapodesta.agentoffice.infrastructure.office;

import com.lucapodesta.agentoffice.domain.agent.AgentType;
import com.lucapodesta.agentoffice.domain.task.TaskEvent;
import com.lucapodesta.agentoffice.domain.task.TaskEventType;
import java.time.Instant;
import java.util.UUID;

/** What the office view needs to animate one audit event. Details are shortened for speech bubbles. */
record OfficeEventMessage(UUID id, UUID taskId, TaskEventType type, AgentType agent, String actor,
                          String details, Instant createdAt) {

    static final int MAX_DETAILS_LENGTH = 200;

    static OfficeEventMessage from(TaskEvent event) {
        String details = event.details();
        if (details != null && details.length() > MAX_DETAILS_LENGTH) {
            details = details.substring(0, MAX_DETAILS_LENGTH - 1) + "…";
        }
        return new OfficeEventMessage(event.id(), event.taskId(), event.type(), event.agentActor().orElse(null),
                event.actor(), details, event.createdAt());
    }
}

package com.lucapodesta.agentoffice.domain.task;

import static org.assertj.core.api.Assertions.assertThat;

import com.lucapodesta.agentoffice.domain.agent.AgentType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TaskEventTest {

    @Test
    void capsDetailsLength() {
        TaskEvent event = TaskEvent.of(UUID.randomUUID(), TaskEventType.EXECUTION_COMPLETED,
                TaskEvent.agent(AgentType.DEVELOPER), "x".repeat(5_000), Instant.now());

        assertThat(event.details()).hasSize(TaskEvent.MAX_DETAILS_LENGTH);
        assertThat(event.actor()).isEqualTo("agent:developer");
    }
}

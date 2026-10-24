package com.lucapodesta.agentoffice.infrastructure.office;

import static org.assertj.core.api.Assertions.assertThat;

import com.lucapodesta.agentoffice.application.dto.TaskEventRecorded;
import com.lucapodesta.agentoffice.domain.agent.AgentType;
import com.lucapodesta.agentoffice.domain.task.TaskEvent;
import com.lucapodesta.agentoffice.domain.task.TaskEventType;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class OfficeStreamBroadcasterTest {

    private static final UUID TASK_ID = UUID.fromString("4f0c2a1e-0000-4000-8000-000000000001");
    private static final Instant NOW = Instant.parse("2026-10-24T12:00:00Z");

    private final OfficeStreamBroadcaster broadcaster = new OfficeStreamBroadcaster(Duration.ofHours(1));

    @AfterEach
    void close() {
        broadcaster.close();
    }

    @Test
    void sendsEachEventToEveryViewer() {
        RecordingEmitter first = new RecordingEmitter(false);
        RecordingEmitter second = new RecordingEmitter(false);
        broadcaster.subscribe(first);
        broadcaster.subscribe(second);

        broadcaster.on(recorded(TaskEventType.EXECUTION_STARTED, TaskEvent.agent(AgentType.DEVELOPER), "attempt=1"));

        assertThat(first.sent).hasSize(1);
        assertThat(second.sent).hasSize(1);
        assertThat(first.payload()).isInstanceOfSatisfying(OfficeEventMessage.class, message -> {
            assertThat(message.taskId()).isEqualTo(TASK_ID);
            assertThat(message.type()).isEqualTo(TaskEventType.EXECUTION_STARTED);
            assertThat(message.agent()).isEqualTo(AgentType.DEVELOPER);
            assertThat(message.details()).isEqualTo("attempt=1");
        });
    }

    @Test
    void dropsViewersThatWentAway() {
        RecordingEmitter gone = new RecordingEmitter(true);
        RecordingEmitter alive = new RecordingEmitter(false);
        broadcaster.subscribe(gone);
        broadcaster.subscribe(alive);

        broadcaster.on(recorded(TaskEventType.TASK_CREATED, TaskEvent.USER, null));
        broadcaster.on(recorded(TaskEventType.TASK_ASSIGNED, TaskEvent.agent(AgentType.MANAGER), "DEVELOPER"));

        assertThat(broadcaster.viewerCount()).isEqualTo(1);
        assertThat(gone.sent).isEmpty();
        assertThat(alive.sent).hasSize(2);
    }

    @Test
    void shortensLongDetailsForSpeechBubbles() {
        TaskEvent event = TaskEvent.of(TASK_ID, TaskEventType.EXECUTION_COMPLETED, TaskEvent.SYSTEM, "x".repeat(500), NOW);

        OfficeEventMessage message = OfficeEventMessage.from(event);

        assertThat(message.details()).hasSize(OfficeEventMessage.MAX_DETAILS_LENGTH).endsWith("…");
        assertThat(message.agent()).isNull();
    }

    private static TaskEventRecorded recorded(TaskEventType type, String actor, String details) {
        return new TaskEventRecorded(TaskEvent.of(TASK_ID, type, actor, details, NOW));
    }

    private static final class RecordingEmitter extends SseEmitter {

        private final boolean broken;
        private final List<SseEventBuilder> sent = new ArrayList<>();

        RecordingEmitter(boolean broken) {
            this.broken = broken;
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            if (broken) {
                throw new IOException("Broken pipe");
            }
            sent.add(builder);
        }

        Object payload() {
            return sent.getFirst().build().stream()
                    .map(part -> part.getData())
                    .filter(data -> !(data instanceof String))
                    .findFirst()
                    .orElseThrow();
        }
    }
}

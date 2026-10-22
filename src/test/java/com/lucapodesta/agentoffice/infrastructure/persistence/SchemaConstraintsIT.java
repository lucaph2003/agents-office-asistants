package com.lucapodesta.agentoffice.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lucapodesta.agentoffice.AbstractIntegrationTest;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/** The database guards the same invariants as the domain, in case anything writes around it. */
class SchemaConstraintsIT extends AbstractIntegrationTest {

    @Autowired
    private Flyway flyway;

    @Test
    void migrationsAreApplied() {
        assertThat(flyway.info().applied()).extracting(info -> info.getVersion().getVersion()).contains("1");
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void rejectsUnknownStatus() {
        assertThatThrownBy(() -> insertTask(UUID.randomUUID(), "SLEEPING", null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_tasks_status");
    }

    @Test
    void terminalStatusRequiresCompletionTime() {
        assertThatThrownBy(() -> insertTask(UUID.randomUUID(), "DONE", null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_tasks_completed_at");
    }

    @Test
    void executionsMustBelongToExistingTask() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO agent_executions (id, task_id, agent_type, status, attempt, model, prompt_version, created_at)
                VALUES (?, ?, 'DEVELOPER', 'PENDING', 1, 'qwen3', 'v1', now())
                """, UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("agent_executions_task_id_fkey");
    }

    @Test
    void failedExecutionNeedsErrorCode() {
        UUID taskId = UUID.randomUUID();
        insertTask(taskId, "IN_PROGRESS", null);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO agent_executions (id, task_id, agent_type, status, attempt, model, prompt_version, created_at)
                VALUES (?, ?, 'DEVELOPER', 'FAILED', 1, 'qwen3', 'v1', now())
                """, UUID.randomUUID(), taskId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_executions_error_code");
    }

    private void insertTask(UUID id, String status, java.sql.Timestamp completedAt) {
        jdbc.update("""
                INSERT INTO tasks (id, title, description, status, priority, source, created_at, updated_at, completed_at)
                VALUES (?, 'title', 'description', ?, 'MEDIUM', 'API', now(), now(), ?)
                """, id, status, completedAt);
    }
}

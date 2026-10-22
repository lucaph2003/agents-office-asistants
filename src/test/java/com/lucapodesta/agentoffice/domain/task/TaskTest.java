package com.lucapodesta.agentoffice.domain.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lucapodesta.agentoffice.domain.agent.AgentType;
import com.lucapodesta.agentoffice.domain.agent.ManagerDecision;
import com.lucapodesta.agentoffice.domain.exception.DomainValidationException;
import com.lucapodesta.agentoffice.domain.exception.ErrorCode;
import com.lucapodesta.agentoffice.domain.exception.InvalidTaskStateException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TaskTest {

    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant T1 = T0.plusSeconds(60);
    private static final ManagerDecision ASSIGN_DEVELOPER =
            new ManagerDecision(AgentType.DEVELOPER, TaskPriority.HIGH, "Needs a technical plan");

    @Test
    void newTaskStartsInInboxWithDefaults() {
        Task task = Task.create("  Migrate to Java 21  ", "Plan the migration", null, TaskSource.TELEGRAM, "chat-1", false, T0);

        assertThat(task.id()).isNotNull();
        assertThat(task.title()).isEqualTo("Migrate to Java 21");
        assertThat(task.status()).isEqualTo(TaskStatus.INBOX);
        assertThat(task.priority()).isEqualTo(TaskPriority.MEDIUM);
        assertThat(task.assignedAgent()).isNull();
        assertThat(task.attempt()).isZero();
        assertThat(task.createdAt()).isEqualTo(T0);
        assertThat(task.updatedAt()).isEqualTo(T0);
        assertThat(task.completedAt()).isNull();
    }

    @Test
    void rejectsBlankOrOversizedText() {
        assertThatThrownBy(() -> Task.create(" ", "desc", null, TaskSource.API, null, false, T0))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("title");
        assertThatThrownBy(() -> Task.create("title", null, null, TaskSource.API, null, false, T0))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("description");
        assertThatThrownBy(() -> Task.create("x".repeat(Task.MAX_TITLE_LENGTH + 1), "desc", null, TaskSource.API, null, false, T0))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("at most");
    }

    @Test
    void happyPathReachesDone() {
        Task task = newTask();

        task.assign(ASSIGN_DEVELOPER, T0);
        assertThat(task.status()).isEqualTo(TaskStatus.PLANNED);
        assertThat(task.assignedAgent()).isEqualTo(AgentType.DEVELOPER);
        assertThat(task.priority()).isEqualTo(TaskPriority.HIGH);

        task.start(T0);
        assertThat(task.status()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(task.attempt()).isEqualTo(1);

        task.submitForReview(T0);
        task.complete(T1);

        assertThat(task.status()).isEqualTo(TaskStatus.DONE);
        assertThat(task.updatedAt()).isEqualTo(T1);
        assertThat(task.completedAt()).isEqualTo(T1);
    }

    @Test
    void requestingChangesStartsANewAttempt() {
        Task task = inReview();

        task.requestChanges(T1);

        assertThat(task.status()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(task.attempt()).isEqualTo(2);
    }

    @Test
    void cannotSkipReview() {
        Task task = newTask();
        task.assign(ASSIGN_DEVELOPER, T0);
        task.start(T0);

        assertThatThrownBy(() -> task.complete(T1))
                .isInstanceOf(InvalidTaskStateException.class)
                .hasMessageContaining("IN_PROGRESS to DONE")
                .extracting("code").isEqualTo(ErrorCode.INVALID_TASK_STATE);
    }

    @Test
    void startRequiresAPlannedTask() {
        Task task = inReview();

        assertThatThrownBy(() -> task.start(T1)).isInstanceOf(InvalidTaskStateException.class);
        assertThat(task.attempt()).isEqualTo(1);
    }

    @Test
    void requestChangesRequiresReview() {
        Task task = newTask();

        assertThatThrownBy(() -> task.requestChanges(T1)).isInstanceOf(InvalidTaskStateException.class);
    }

    @Test
    void terminalTaskCannotBeCancelled() {
        Task task = inReview();
        task.complete(T1);

        assertThatThrownBy(() -> task.cancel(T1)).isInstanceOf(InvalidTaskStateException.class);
        assertThat(task.status()).isEqualTo(TaskStatus.DONE);
    }

    @Test
    void cancelAndFailRecordCompletionTime() {
        Task cancelled = newTask();
        cancelled.cancel(T1);
        Task failed = inReview();
        failed.fail(T1);

        assertThat(cancelled.status()).isEqualTo(TaskStatus.CANCELLED);
        assertThat(cancelled.completedAt()).isEqualTo(T1);
        assertThat(failed.status()).isEqualTo(TaskStatus.FAILED);
        assertThat(failed.completedAt()).isEqualTo(T1);
    }

    @Test
    void blockedUnassignedTaskResumesToInbox() {
        Task task = newTask();
        task.block(T0);

        task.resume(T1);

        assertThat(task.status()).isEqualTo(TaskStatus.INBOX);
    }

    @Test
    void blockedAssignedTaskResumesToPlanned() {
        Task task = newTask();
        task.assign(ASSIGN_DEVELOPER, T0);
        task.start(T0);
        task.block(T0);

        task.resume(T1);

        assertThat(task.status()).isEqualTo(TaskStatus.PLANNED);
        assertThat(task.assignedAgent()).isEqualTo(AgentType.DEVELOPER);
    }

    @Test
    void onlyBlockedTasksCanResume() {
        Task task = newTask();

        assertThatThrownBy(() -> task.resume(T1)).isInstanceOf(InvalidTaskStateException.class);
    }

    @Test
    void failedTransitionLeavesTaskUntouched() {
        Task task = newTask();

        assertThatThrownBy(() -> task.submitForReview(T1)).isInstanceOf(InvalidTaskStateException.class);
        assertThat(task.status()).isEqualTo(TaskStatus.INBOX);
        assertThat(task.updatedAt()).isEqualTo(T0);
    }

    private static Task newTask() {
        return Task.create("Migrate to Java 21", "Plan the migration", TaskPriority.MEDIUM, TaskSource.API, null, false, T0);
    }

    private static Task inReview() {
        Task task = newTask();
        task.assign(ASSIGN_DEVELOPER, T0);
        task.start(T0);
        task.submitForReview(T0);
        return task;
    }
}

package com.lucapodesta.agentoffice.domain.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class TaskStatusTest {

    @ParameterizedTest
    @CsvSource({
            "INBOX, PLANNED", "INBOX, BLOCKED", "INBOX, FAILED", "INBOX, CANCELLED",
            "PLANNED, IN_PROGRESS", "PLANNED, CANCELLED",
            "IN_PROGRESS, REVIEW", "IN_PROGRESS, BLOCKED", "IN_PROGRESS, FAILED", "IN_PROGRESS, CANCELLED",
            "REVIEW, DONE", "REVIEW, IN_PROGRESS", "REVIEW, BLOCKED", "REVIEW, FAILED", "REVIEW, CANCELLED",
            "BLOCKED, INBOX", "BLOCKED, PLANNED", "BLOCKED, FAILED", "BLOCKED, CANCELLED"
    })
    void allowsLifecycleTransitions(TaskStatus from, TaskStatus to) {
        assertThat(from.canTransitionTo(to)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "INBOX, IN_PROGRESS", "INBOX, DONE",
            "PLANNED, REVIEW", "PLANNED, DONE",
            "IN_PROGRESS, DONE", "IN_PROGRESS, PLANNED",
            "REVIEW, PLANNED",
            "BLOCKED, DONE", "BLOCKED, IN_PROGRESS"
    })
    void rejectsShortcuts(TaskStatus from, TaskStatus to) {
        assertThat(from.canTransitionTo(to)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"DONE", "FAILED", "CANCELLED"})
    void terminalStatusesAllowNoTransition(TaskStatus terminal) {
        assertThat(terminal.isTerminal()).isTrue();
        assertThat(EnumSet.allOf(TaskStatus.class)).noneMatch(terminal::canTransitionTo);
    }

    @Test
    void noStatusTransitionsToItself() {
        assertThat(EnumSet.allOf(TaskStatus.class)).noneMatch(status -> status.canTransitionTo(status));
    }
}

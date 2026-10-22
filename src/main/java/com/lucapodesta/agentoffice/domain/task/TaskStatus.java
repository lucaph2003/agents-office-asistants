package com.lucapodesta.agentoffice.domain.task;

import java.util.EnumSet;
import java.util.Set;

public enum TaskStatus {
    INBOX,
    PLANNED,
    IN_PROGRESS,
    REVIEW,
    BLOCKED,
    DONE,
    FAILED,
    CANCELLED;

    public boolean canTransitionTo(TaskStatus target) {
        return allowedTargets().contains(target);
    }

    public boolean isTerminal() {
        return allowedTargets().isEmpty();
    }

    private Set<TaskStatus> allowedTargets() {
        return switch (this) {
            case INBOX -> EnumSet.of(PLANNED, BLOCKED, FAILED, CANCELLED);
            case PLANNED -> EnumSet.of(IN_PROGRESS, CANCELLED);
            case IN_PROGRESS -> EnumSet.of(REVIEW, BLOCKED, FAILED, CANCELLED);
            case REVIEW -> EnumSet.of(DONE, IN_PROGRESS, BLOCKED, FAILED, CANCELLED);
            case BLOCKED -> EnumSet.of(INBOX, PLANNED, FAILED, CANCELLED);
            case DONE, FAILED, CANCELLED -> EnumSet.noneOf(TaskStatus.class);
        };
    }
}

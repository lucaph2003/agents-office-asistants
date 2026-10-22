package com.lucapodesta.agentoffice.domain.task;

import com.lucapodesta.agentoffice.domain.agent.AgentType;
import com.lucapodesta.agentoffice.domain.agent.ManagerDecision;
import com.lucapodesta.agentoffice.domain.exception.DomainValidationException;
import com.lucapodesta.agentoffice.domain.exception.InvalidTaskStateException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class Task {

    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MAX_DESCRIPTION_LENGTH = 10_000;

    private final UUID id;
    private final String title;
    private final String description;
    private final TaskSource source;
    private final String externalReference;
    private final boolean requiresHumanApproval;
    private final Instant createdAt;

    private TaskStatus status;
    private TaskPriority priority;
    private AgentType assignedAgent;
    private int attempt;
    private Instant updatedAt;
    private Instant completedAt;

    private Task(UUID id, String title, String description, TaskSource source, String externalReference,
                 boolean requiresHumanApproval, TaskPriority priority, Instant createdAt) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.source = source;
        this.externalReference = externalReference;
        this.requiresHumanApproval = requiresHumanApproval;
        this.priority = priority;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        this.status = TaskStatus.INBOX;
    }

    public static Task create(String title, String description, TaskPriority priority, TaskSource source,
                              String externalReference, boolean requiresHumanApproval, Instant now) {
        return new Task(
                UUID.randomUUID(),
                requireText(title, "title", MAX_TITLE_LENGTH),
                requireText(description, "description", MAX_DESCRIPTION_LENGTH),
                Objects.requireNonNull(source, "source"),
                externalReference,
                requiresHumanApproval,
                priority == null ? TaskPriority.MEDIUM : priority,
                Objects.requireNonNull(now, "now"));
    }

    /** Rebuilds a task from persisted state. Does not re-run creation rules. */
    public static Task restore(UUID id, String title, String description, TaskStatus status, TaskPriority priority,
                               AgentType assignedAgent, TaskSource source, String externalReference,
                               boolean requiresHumanApproval, int attempt, Instant createdAt, Instant updatedAt,
                               Instant completedAt) {
        Task task = new Task(id, title, description, source, externalReference, requiresHumanApproval, priority, createdAt);
        task.status = status;
        task.assignedAgent = assignedAgent;
        task.attempt = attempt;
        task.updatedAt = updatedAt;
        task.completedAt = completedAt;
        return task;
    }

    public void assign(ManagerDecision decision, Instant now) {
        moveTo(TaskStatus.PLANNED, now);
        this.assignedAgent = decision.agent();
        this.priority = decision.priority();
    }

    /** Starts a worker attempt on a planned task. */
    public void start(Instant now) {
        requireStatus(TaskStatus.PLANNED, TaskStatus.IN_PROGRESS);
        moveTo(TaskStatus.IN_PROGRESS, now);
        attempt++;
    }

    public void submitForReview(Instant now) {
        moveTo(TaskStatus.REVIEW, now);
    }

    /** Sends the task back to its worker for another attempt. */
    public void requestChanges(Instant now) {
        requireStatus(TaskStatus.REVIEW, TaskStatus.IN_PROGRESS);
        moveTo(TaskStatus.IN_PROGRESS, now);
        attempt++;
    }

    public void complete(Instant now) {
        moveTo(TaskStatus.DONE, now);
        completedAt = now;
    }

    public void fail(Instant now) {
        moveTo(TaskStatus.FAILED, now);
        completedAt = now;
    }

    public void cancel(Instant now) {
        moveTo(TaskStatus.CANCELLED, now);
        completedAt = now;
    }

    /** Parks the task after an infrastructure problem (e.g. model unavailable) so it can be resumed later. */
    public void block(Instant now) {
        moveTo(TaskStatus.BLOCKED, now);
    }

    /** Resumes a blocked task from the last step that still makes sense: routing if unassigned, otherwise execution. */
    public void resume(Instant now) {
        TaskStatus target = assignedAgent == null ? TaskStatus.INBOX : TaskStatus.PLANNED;
        requireStatus(TaskStatus.BLOCKED, target);
        moveTo(target, now);
    }

    private void moveTo(TaskStatus target, Instant now) {
        Objects.requireNonNull(now, "now");
        if (!status.canTransitionTo(target)) {
            throw new InvalidTaskStateException(id, status, target);
        }
        status = target;
        updatedAt = now;
    }

    private void requireStatus(TaskStatus expected, TaskStatus target) {
        if (status != expected) {
            throw new InvalidTaskStateException(id, status, target);
        }
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException("Task " + field + " must not be blank");
        }
        String trimmed = value.strip();
        if (trimmed.length() > maxLength) {
            throw new DomainValidationException("Task " + field + " must be at most " + maxLength + " characters");
        }
        return trimmed;
    }

    public UUID id() { return id; }
    public String title() { return title; }
    public String description() { return description; }
    public TaskStatus status() { return status; }
    public TaskPriority priority() { return priority; }
    public AgentType assignedAgent() { return assignedAgent; }
    public TaskSource source() { return source; }
    public String externalReference() { return externalReference; }
    public boolean requiresHumanApproval() { return requiresHumanApproval; }
    public int attempt() { return attempt; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public Instant completedAt() { return completedAt; }
}

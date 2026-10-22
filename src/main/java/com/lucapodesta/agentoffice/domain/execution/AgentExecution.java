package com.lucapodesta.agentoffice.domain.execution;

import com.lucapodesta.agentoffice.domain.agent.AgentType;
import com.lucapodesta.agentoffice.domain.exception.DomainValidationException;
import com.lucapodesta.agentoffice.domain.exception.ErrorCode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** One invocation of one agent for a task. A task accumulates several of these across its lifecycle. */
public class AgentExecution {

    public static final int MAX_OUTPUT_LENGTH = 20_000;
    public static final int MAX_SUMMARY_LENGTH = 1_000;
    static final String TRUNCATION_MARKER = "\n[output truncated]";

    private final UUID id;
    private final UUID taskId;
    private final AgentType agentType;
    private final int attempt;
    private final String model;
    private final String promptVersion;
    private final Instant createdAt;

    private ExecutionStatus status;
    private String output;
    private String summary;
    private ErrorCode errorCode;
    private Instant startedAt;
    private Instant finishedAt;

    private AgentExecution(UUID id, UUID taskId, AgentType agentType, int attempt, String model,
                           String promptVersion, Instant createdAt) {
        this.id = id;
        this.taskId = taskId;
        this.agentType = agentType;
        this.attempt = attempt;
        this.model = model;
        this.promptVersion = promptVersion;
        this.createdAt = createdAt;
        this.status = ExecutionStatus.PENDING;
    }

    public static AgentExecution create(UUID taskId, AgentType agentType, int attempt, String model,
                                        String promptVersion, Instant now) {
        if (attempt < 1) {
            throw new DomainValidationException("Execution attempt must be at least 1");
        }
        return new AgentExecution(
                UUID.randomUUID(),
                Objects.requireNonNull(taskId, "taskId"),
                Objects.requireNonNull(agentType, "agentType"),
                attempt,
                Objects.requireNonNull(model, "model"),
                Objects.requireNonNull(promptVersion, "promptVersion"),
                Objects.requireNonNull(now, "now"));
    }

    /** Rebuilds an execution from persisted state. */
    public static AgentExecution restore(UUID id, UUID taskId, AgentType agentType, int attempt, String model,
                                         String promptVersion, ExecutionStatus status, String output,
                                         String summary, ErrorCode errorCode, Instant createdAt, Instant startedAt,
                                         Instant finishedAt) {
        AgentExecution execution = new AgentExecution(id, taskId, agentType, attempt, model, promptVersion, createdAt);
        execution.status = status;
        execution.output = output;
        execution.summary = summary;
        execution.errorCode = errorCode;
        execution.startedAt = startedAt;
        execution.finishedAt = finishedAt;
        return execution;
    }

    public void start(Instant now) {
        requireStatus(ExecutionStatus.PENDING, ExecutionStatus.RUNNING);
        status = ExecutionStatus.RUNNING;
        startedAt = now;
    }

    /**
     * @param output  full result of the agent
     * @param summary short human-readable version, used in notifications and on the board
     */
    public void succeed(String output, String summary, Instant now) {
        requireStatus(ExecutionStatus.RUNNING, ExecutionStatus.SUCCESS);
        status = ExecutionStatus.SUCCESS;
        this.output = truncate(Objects.requireNonNull(output, "output"), MAX_OUTPUT_LENGTH);
        this.summary = summary == null ? null : truncate(summary.strip(), MAX_SUMMARY_LENGTH);
        finishedAt = now;
    }

    public void fail(ErrorCode errorCode, Instant now) {
        requireStatus(ExecutionStatus.RUNNING, ExecutionStatus.FAILED);
        status = ExecutionStatus.FAILED;
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
        finishedAt = now;
    }

    /** Marks a successful execution whose output was turned down by review. */
    public void reject() {
        requireStatus(ExecutionStatus.SUCCESS, ExecutionStatus.REJECTED);
        status = ExecutionStatus.REJECTED;
    }

    private void requireStatus(ExecutionStatus expected, ExecutionStatus target) {
        if (status != expected) {
            throw new IllegalStateException(
                    "Execution %s cannot move from %s to %s".formatted(id, status, target));
        }
    }

    private static String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - TRUNCATION_MARKER.length()) + TRUNCATION_MARKER;
    }

    public UUID id() { return id; }
    public UUID taskId() { return taskId; }
    public AgentType agentType() { return agentType; }
    public int attempt() { return attempt; }
    public String model() { return model; }
    public String promptVersion() { return promptVersion; }
    public ExecutionStatus status() { return status; }
    public String output() { return output; }
    public String summary() { return summary; }
    public ErrorCode errorCode() { return errorCode; }
    public Instant createdAt() { return createdAt; }
    public Instant startedAt() { return startedAt; }
    public Instant finishedAt() { return finishedAt; }
}

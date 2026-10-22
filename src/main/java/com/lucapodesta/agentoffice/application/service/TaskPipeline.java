package com.lucapodesta.agentoffice.application.service;

import com.lucapodesta.agentoffice.application.agent.AgentReply;
import com.lucapodesta.agentoffice.application.agent.AgentTeam;
import com.lucapodesta.agentoffice.application.agent.LlmAgent;
import com.lucapodesta.agentoffice.application.agent.Revision;
import com.lucapodesta.agentoffice.application.agent.ReviewVerdict;
import com.lucapodesta.agentoffice.application.agent.WorkerAgent;
import com.lucapodesta.agentoffice.application.port.out.AgentExecutionRepository;
import com.lucapodesta.agentoffice.application.port.out.ReviewRepository;
import com.lucapodesta.agentoffice.application.port.out.TaskEventRepository;
import com.lucapodesta.agentoffice.application.port.out.TaskRepository;
import com.lucapodesta.agentoffice.domain.agent.AgentType;
import com.lucapodesta.agentoffice.domain.exception.AgentOfficeException;
import com.lucapodesta.agentoffice.domain.exception.ErrorCode;
import com.lucapodesta.agentoffice.domain.exception.InvalidAgentOutputException;
import com.lucapodesta.agentoffice.domain.exception.InvalidTaskStateException;
import com.lucapodesta.agentoffice.domain.exception.TaskNotFoundException;
import com.lucapodesta.agentoffice.domain.execution.AgentExecution;
import com.lucapodesta.agentoffice.domain.execution.ExecutionStatus;
import com.lucapodesta.agentoffice.domain.review.Review;
import com.lucapodesta.agentoffice.domain.review.ReviewOutcome;
import com.lucapodesta.agentoffice.domain.review.ReviewPolicy;
import com.lucapodesta.agentoffice.domain.review.ReviewStatus;
import com.lucapodesta.agentoffice.domain.review.Reviewer;
import com.lucapodesta.agentoffice.domain.task.Task;
import com.lucapodesta.agentoffice.domain.task.TaskEvent;
import com.lucapodesta.agentoffice.domain.task.TaskEventType;
import com.lucapodesta.agentoffice.domain.task.TaskStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Drives a task through Manager → worker → Reviewer until it is done, failed, blocked or
 * waiting for a human. Each step re-reads the task, so a cancellation in the middle of a long
 * model call simply stops the pipeline. Model calls never run inside a database transaction.
 */
public class TaskPipeline {

    private static final Logger log = LoggerFactory.getLogger(TaskPipeline.class);

    private final TaskRepository tasks;
    private final AgentExecutionRepository executions;
    private final ReviewRepository reviews;
    private final TaskEventRepository events;
    private final AgentTeam team;
    private final ReviewPolicy reviewPolicy;
    private final int maxOutputAttempts;
    private final TransactionOperations transactions;
    private final Clock clock;

    public TaskPipeline(TaskRepository tasks, AgentExecutionRepository executions, ReviewRepository reviews,
                        TaskEventRepository events, AgentTeam team, ReviewPolicy reviewPolicy,
                        int maxOutputAttempts, TransactionOperations transactions, Clock clock) {
        this.tasks = tasks;
        this.executions = executions;
        this.reviews = reviews;
        this.events = events;
        this.team = team;
        this.reviewPolicy = reviewPolicy;
        this.maxOutputAttempts = maxOutputAttempts;
        this.transactions = transactions;
        this.clock = clock;
    }

    public void process(UUID taskId) {
        MDC.put("taskId", taskId.toString());
        try {
            boolean proceed = true;
            while (proceed) {
                Task task = load(taskId);
                proceed = switch (task.status()) {
                    case INBOX -> route(task);
                    case PLANNED, IN_PROGRESS -> work(task);
                    case REVIEW -> review(task);
                    case BLOCKED, DONE, FAILED, CANCELLED -> false;
                };
            }
            log.info("event=PIPELINE_FINISHED status={}", load(taskId).status());
        } catch (InvalidTaskStateException | OptimisticLockingFailureException ex) {
            log.info("event=PIPELINE_STOPPED reason=\"task changed concurrently: {}\"", ex.getMessage());
        } finally {
            MDC.remove("taskId");
        }
    }

    private boolean route(Task task) {
        return call(task, team.manager(), 1, () -> team.manager().route(task))
                .map(done -> update(task.id(), TaskStatus.INBOX, current -> {
                    current.assign(done.reply().value(), now());
                    return List.of(event(current, TaskEventType.TASK_ASSIGNED, AgentType.MANAGER, done.reply().summary()));
                }))
                .orElse(false);
    }

    private boolean work(Task task) {
        if (task.status() == TaskStatus.PLANNED
                && !update(task.id(), TaskStatus.PLANNED, current -> {
                    current.start(now());
                    return List.of();
                })) {
            return false;
        }
        Task current = load(task.id());
        WorkerAgent worker = team.worker(current.assignedAgent());
        Revision revision = current.attempt() > 1 ? lastRevision(current) : null;
        return call(current, worker, current.attempt(), () -> worker.work(current, revision))
                .map(done -> update(current.id(), TaskStatus.IN_PROGRESS, inProgress -> {
                    inProgress.submitForReview(now());
                    return List.of(TaskEvent.of(inProgress.id(), TaskEventType.REVIEW_STARTED, TaskEvent.SYSTEM,
                            "attempt=" + inProgress.attempt(), now()));
                }))
                .orElse(false);
    }

    private boolean review(Task task) {
        if (awaitingHumanApproval(task)) {
            return false;
        }
        AgentExecution work = latestWork(task);
        Optional<Done<ReviewVerdict>> result =
                call(task, team.reviewer(), task.attempt(), () -> team.reviewer().review(task, work.output()));
        if (result.isEmpty()) {
            return false;
        }
        ReviewVerdict verdict = result.get().reply().value();
        ReviewOutcome outcome = reviewPolicy.decide(task, verdict.status());
        return update(task.id(), TaskStatus.REVIEW, current -> applyReview(current, work, verdict, outcome));
    }

    private List<TaskEvent> applyReview(Task task, AgentExecution work, ReviewVerdict verdict, ReviewOutcome outcome) {
        Instant now = now();
        reviews.save(Review.of(task.id(), work.id(), Reviewer.AGENT, verdict.status(), verdict.feedback(), now));
        List<TaskEvent> recorded = new ArrayList<>();
        if (outcome != ReviewOutcome.COMPLETE && outcome != ReviewOutcome.AWAIT_HUMAN_APPROVAL) {
            work.reject();
            executions.save(work);
        }
        switch (outcome) {
            case COMPLETE -> {
                recorded.add(event(task, TaskEventType.REVIEW_APPROVED, AgentType.REVIEWER, verdict.feedback()));
                task.complete(now);
                recorded.add(TaskEvent.of(task.id(), TaskEventType.TASK_COMPLETED, TaskEvent.SYSTEM,
                        "agent=%s attempts=%d".formatted(task.assignedAgent(), task.attempt()), now));
            }
            case AWAIT_HUMAN_APPROVAL -> {
                recorded.add(event(task, TaskEventType.REVIEW_APPROVED, AgentType.REVIEWER, verdict.feedback()));
                recorded.add(TaskEvent.of(task.id(), TaskEventType.HUMAN_APPROVAL_REQUESTED, TaskEvent.SYSTEM, null, now));
            }
            case RETRY -> {
                recorded.add(event(task, TaskEventType.CHANGES_REQUESTED, AgentType.REVIEWER, verdict.feedback()));
                task.requestChanges(now);
            }
            case FAIL_MAX_RETRIES -> {
                recorded.add(event(task, TaskEventType.CHANGES_REQUESTED, AgentType.REVIEWER, verdict.feedback()));
                task.fail(now);
                recorded.add(TaskEvent.of(task.id(), TaskEventType.TASK_FAILED, TaskEvent.SYSTEM,
                        "%s after %d attempts".formatted(ErrorCode.MAX_RETRIES_EXCEEDED, task.attempt()), now));
            }
            case FAIL_REJECTED -> {
                task.fail(now);
                recorded.add(event(task, TaskEventType.TASK_FAILED, AgentType.REVIEWER,
                        "Rejected by reviewer: " + verdict.feedback()));
            }
        }
        return recorded;
    }

    /**
     * Runs one agent step and records it as an execution. Unusable output is retried a bounded
     * number of times; an unavailable model blocks the task so it can be resumed later.
     */
    private <T> Optional<Done<T>> call(Task task, LlmAgent agent, int attempt, Supplier<AgentReply<T>> invocation) {
        for (int outputAttempt = 1; ; outputAttempt++) {
            AgentExecution execution = AgentExecution.create(task.id(), agent.type(), attempt, agent.model(),
                    agent.promptVersion(), now());
            execution.start(now());
            record(execution, event(task, TaskEventType.EXECUTION_STARTED, agent.type(),
                    "attempt=%d model=%s prompt=%s".formatted(attempt, execution.model(), execution.promptVersion())));
            try {
                AgentReply<T> reply = invocation.get();
                execution.succeed(reply.output(), reply.summary(), now());
                record(execution, event(task, TaskEventType.EXECUTION_COMPLETED, agent.type(), execution.summary()));
                return Optional.of(new Done<>(reply, execution));
            } catch (InvalidAgentOutputException ex) {
                recordFailure(task, agent, execution, ex.code(), ex.getMessage());
                if (outputAttempt >= maxOutputAttempts) {
                    update(task.id(), task.status(), current -> {
                        current.fail(now());
                        return List.of(event(current, TaskEventType.TASK_FAILED, agent.type(),
                                "%s: %s".formatted(ex.code(), ex.getMessage())));
                    });
                    return Optional.empty();
                }
            } catch (AgentOfficeException ex) {
                recordFailure(task, agent, execution, ex.code(), ex.getMessage());
                block(task, ex.code(), ex.getMessage());
                return Optional.empty();
            } catch (RuntimeException ex) {
                log.error("event=EXECUTION_CRASHED agent={}", agent.type(), ex);
                String reason = "Unexpected error in " + agent.type();
                recordFailure(task, agent, execution, ErrorCode.INTERNAL_ERROR, reason);
                block(task, ErrorCode.INTERNAL_ERROR, reason);
                return Optional.empty();
            }
        }
    }

    private void recordFailure(Task task, LlmAgent agent, AgentExecution execution, ErrorCode code, String reason) {
        execution.fail(code, now());
        record(execution, event(task, TaskEventType.EXECUTION_FAILED, agent.type(), code + ": " + reason));
    }

    private void block(Task task, ErrorCode code, String reason) {
        update(task.id(), task.status(), current -> {
            current.block(now());
            return List.of(TaskEvent.of(current.id(), TaskEventType.TASK_BLOCKED, TaskEvent.SYSTEM,
                    code + ": " + reason, now()));
        });
    }

    /**
     * Applies a state change only if the task is still where the pipeline left it.
     *
     * @return false when the task moved on in the meantime (e.g. it was cancelled)
     */
    private boolean update(UUID taskId, TaskStatus expected, Function<Task, List<TaskEvent>> change) {
        Boolean applied = transactions.execute(status -> {
            Task task = load(taskId);
            if (task.status() != expected) {
                log.info("event=PIPELINE_STOPPED reason=\"expected {} but task is {}\"", expected, task.status());
                return false;
            }
            List<TaskEvent> recorded = change.apply(task);
            tasks.save(task);
            recorded.forEach(this::append);
            return true;
        });
        return Boolean.TRUE.equals(applied);
    }

    private void record(AgentExecution execution, TaskEvent event) {
        transactions.executeWithoutResult(status -> {
            executions.save(execution);
            append(event);
        });
    }

    private void append(TaskEvent event) {
        events.append(event);
        log.info("event={} actor={} details=\"{}\"", event.type(), event.actor(), event.details());
    }

    private Revision lastRevision(Task task) {
        List<Review> taskReviews = reviews.findByTaskId(task.id());
        if (taskReviews.isEmpty()) {
            return null;
        }
        Review last = taskReviews.getLast();
        return executions.findByTaskId(task.id()).stream()
                .filter(execution -> execution.id().equals(last.executionId()))
                .findFirst()
                .map(execution -> new Revision(execution.output(), last.feedback()))
                .orElse(null);
    }

    private AgentExecution latestWork(Task task) {
        return executions.findByTaskId(task.id()).stream()
                .filter(execution -> execution.agentType() == task.assignedAgent())
                .filter(execution -> execution.status() == ExecutionStatus.SUCCESS)
                .filter(execution -> execution.attempt() == task.attempt())
                .reduce((first, second) -> second)
                .orElseThrow(() -> new IllegalStateException("Task " + task.id() + " is in review without output"));
    }

    private boolean awaitingHumanApproval(Task task) {
        if (!task.requiresHumanApproval()) {
            return false;
        }
        List<Review> taskReviews = reviews.findByTaskId(task.id());
        return !taskReviews.isEmpty() && taskReviews.getLast().status() == ReviewStatus.APPROVED;
    }

    private Task load(UUID taskId) {
        return tasks.findById(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));
    }

    private TaskEvent event(Task task, TaskEventType type, AgentType agent, String details) {
        return TaskEvent.of(task.id(), type, TaskEvent.agent(agent), details, now());
    }

    private Instant now() {
        return clock.instant();
    }

    private record Done<T>(AgentReply<T> reply, AgentExecution execution) {
    }
}

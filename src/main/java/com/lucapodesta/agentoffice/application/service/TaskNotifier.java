package com.lucapodesta.agentoffice.application.service;

import com.lucapodesta.agentoffice.application.port.out.AgentExecutionRepository;
import com.lucapodesta.agentoffice.application.port.out.BoardCard;
import com.lucapodesta.agentoffice.application.port.out.BoardCardRepository;
import com.lucapodesta.agentoffice.application.port.out.NotificationPort;
import com.lucapodesta.agentoffice.application.port.out.ReviewRepository;
import com.lucapodesta.agentoffice.application.port.out.TaskNotification;
import com.lucapodesta.agentoffice.application.port.out.TaskRepository;
import com.lucapodesta.agentoffice.domain.exception.TaskNotFoundException;
import com.lucapodesta.agentoffice.domain.execution.AgentExecution;
import com.lucapodesta.agentoffice.domain.execution.ExecutionStatus;
import com.lucapodesta.agentoffice.domain.review.Review;
import com.lucapodesta.agentoffice.domain.task.Task;
import com.lucapodesta.agentoffice.domain.task.TaskEvent;
import com.lucapodesta.agentoffice.domain.task.TaskEventType;
import java.time.Clock;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Sends a notification when a task needs the user's attention: it finished, failed, got
 * blocked or waits for approval. Delivery problems are recorded and never affect the task.
 */
public class TaskNotifier {

    private static final Logger log = LoggerFactory.getLogger(TaskNotifier.class);

    private static final Set<TaskEventType> NOTIFY_ON = EnumSet.of(
            TaskEventType.TASK_COMPLETED, TaskEventType.TASK_FAILED, TaskEventType.TASK_BLOCKED,
            TaskEventType.HUMAN_APPROVAL_REQUESTED);

    private final NotificationPort notifications;
    private final TaskRepository tasks;
    private final AgentExecutionRepository executions;
    private final ReviewRepository reviews;
    private final BoardCardRepository boardCards;
    private final TaskEventRecorder recorder;
    private final TransactionOperations transactions;
    private final Clock clock;

    public TaskNotifier(NotificationPort notifications, TaskRepository tasks, AgentExecutionRepository executions,
                        ReviewRepository reviews, BoardCardRepository boardCards, TaskEventRecorder recorder,
                        TransactionOperations transactions, Clock clock) {
        this.notifications = notifications;
        this.tasks = tasks;
        this.executions = executions;
        this.reviews = reviews;
        this.boardCards = boardCards;
        this.recorder = recorder;
        this.transactions = transactions;
        this.clock = clock;
    }

    public void onEvent(TaskEvent event) {
        if (!NOTIFY_ON.contains(event.type())) {
            return;
        }
        TaskEvent outcome;
        try {
            notifications.notify(transactions.execute(status -> notificationFor(event)));
            outcome = TaskEvent.of(event.taskId(), TaskEventType.USER_NOTIFIED, TaskEvent.SYSTEM,
                    "event=" + event.type(), clock.instant());
        } catch (RuntimeException ex) {
            log.warn("event=NOTIFICATION_FAILED taskId={} trigger={} reason=\"{}\"", event.taskId(), event.type(), ex.getMessage());
            outcome = TaskEvent.of(event.taskId(), TaskEventType.NOTIFICATION_FAILED, TaskEvent.SYSTEM,
                    "after %s: %s".formatted(event.type(), ex.getMessage()), clock.instant());
        }
        TaskEvent recorded = outcome;
        transactions.executeWithoutResult(status -> recorder.record(recorded));
    }

    private TaskNotification notificationFor(TaskEvent event) {
        Task task = tasks.findById(event.taskId()).orElseThrow(() -> new TaskNotFoundException(event.taskId()));
        AgentExecution work = executions.findByTaskId(task.id()).stream()
                .filter(execution -> execution.agentType().isWorker())
                .filter(execution -> execution.status() != ExecutionStatus.FAILED)
                .reduce((first, second) -> second)
                .orElse(null);
        List<Review> taskReviews = reviews.findByTaskId(task.id());
        boolean failure = event.type() == TaskEventType.TASK_FAILED || event.type() == TaskEventType.TASK_BLOCKED;
        return new TaskNotification(
                event.type(), task.id(), task.title(), task.status(), task.priority(), task.assignedAgent(),
                task.attempt(),
                taskReviews.isEmpty() ? null : taskReviews.getLast().status(),
                work == null ? null : work.summary(),
                failure ? event.details() : null,
                boardCards.findByTaskId(task.id()).map(BoardCard::url).orElse(null),
                task.source(), task.externalReference());
    }
}

package com.lucapodesta.agentoffice.application.service;

import com.lucapodesta.agentoffice.application.port.out.AgentExecutionRepository;
import com.lucapodesta.agentoffice.application.port.out.BoardCard;
import com.lucapodesta.agentoffice.application.port.out.BoardCardRepository;
import com.lucapodesta.agentoffice.application.port.out.ReviewRepository;
import com.lucapodesta.agentoffice.application.port.out.TaskBoardPort;
import com.lucapodesta.agentoffice.application.port.out.TaskCard;
import com.lucapodesta.agentoffice.application.port.out.TaskRepository;
import com.lucapodesta.agentoffice.domain.agent.AgentType;
import com.lucapodesta.agentoffice.domain.exception.TaskNotFoundException;
import com.lucapodesta.agentoffice.domain.execution.AgentExecution;
import com.lucapodesta.agentoffice.domain.review.Review;
import com.lucapodesta.agentoffice.domain.task.Task;
import com.lucapodesta.agentoffice.domain.task.TaskEvent;
import com.lucapodesta.agentoffice.domain.task.TaskEventType;
import java.time.Clock;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Mirrors tasks on the board after each committed audit event: creates the card on first sight,
 * moves it with the task's status, refreshes its content and comments on meaningful steps.
 * Every update renders the full current state, so a failed call is repaired by the next event.
 * Board problems are recorded and swallowed; they never affect the task itself.
 */
public class TaskBoardSynchronizer {

    private static final Logger log = LoggerFactory.getLogger(TaskBoardSynchronizer.class);
    private static final int MAX_COMMENT_DETAILS = 500;

    /** Events after which the card's column or content may be out of date. */
    private static final Set<TaskEventType> SYNC_EVENTS = EnumSet.of(
            TaskEventType.TASK_CREATED, TaskEventType.TASK_ASSIGNED, TaskEventType.EXECUTION_STARTED,
            TaskEventType.EXECUTION_COMPLETED, TaskEventType.EXECUTION_FAILED, TaskEventType.REVIEW_STARTED,
            TaskEventType.REVIEW_APPROVED, TaskEventType.CHANGES_REQUESTED, TaskEventType.HUMAN_APPROVAL_REQUESTED,
            TaskEventType.TASK_BLOCKED, TaskEventType.TASK_RESUMED, TaskEventType.TASK_COMPLETED,
            TaskEventType.TASK_FAILED, TaskEventType.TASK_CANCELLED);

    private final TaskBoardPort board;
    private final BoardCardRepository cards;
    private final TaskRepository tasks;
    private final AgentExecutionRepository executions;
    private final ReviewRepository reviews;
    private final TaskEventRecorder recorder;
    private final TransactionOperations transactions;
    private final Clock clock;

    public TaskBoardSynchronizer(TaskBoardPort board, BoardCardRepository cards, TaskRepository tasks,
                                 AgentExecutionRepository executions, ReviewRepository reviews,
                                 TaskEventRecorder recorder, TransactionOperations transactions, Clock clock) {
        this.board = board;
        this.cards = cards;
        this.tasks = tasks;
        this.executions = executions;
        this.reviews = reviews;
        this.recorder = recorder;
        this.transactions = transactions;
        this.clock = clock;
    }

    public void onEvent(TaskEvent event) {
        if (!relevant(event)) {
            return;
        }
        try {
            Snapshot snapshot = transactions.execute(status -> snapshot(event));
            TaskCard view = snapshot.view();
            BoardCard card = snapshot.card();
            if (card == null) {
                card = board.createCard(view);
                remember(event, card.withStatus(view.task().status()), "Card created in " + view.task().status());
            } else {
                board.updateCard(card.id(), view);
                if (card.syncedStatus() != view.task().status()) {
                    remember(event, card.withStatus(view.task().status()), "Card moved to " + view.task().status());
                }
            }
            String cardId = card.id();
            comment(event).ifPresent(text -> board.addComment(cardId, text));
        } catch (RuntimeException ex) {
            log.warn("event=TRELLO_SYNC_FAILED taskId={} trigger={} reason=\"{}\"", event.taskId(), event.type(), ex.getMessage());
            transactions.executeWithoutResult(status -> recorder.record(TaskEvent.of(event.taskId(),
                    TaskEventType.TRELLO_SYNC_FAILED, TaskEvent.SYSTEM, "after %s: %s".formatted(event.type(), ex.getMessage()),
                    clock.instant())));
        }
    }

    /** Manager and Reviewer executions are internal steps; only worker executions show on the board. */
    private static boolean relevant(TaskEvent event) {
        if (!SYNC_EVENTS.contains(event.type())) {
            return false;
        }
        boolean executionEvent = event.type() == TaskEventType.EXECUTION_STARTED
                || event.type() == TaskEventType.EXECUTION_COMPLETED;
        return !executionEvent || event.agentActor().filter(AgentType::isWorker).isPresent();
    }

    /**
     * Read in a transaction of its own: this runs after another transaction committed, and that
     * transaction's persistence context may still hold stale copies (e.g. of the card link).
     */
    private Snapshot snapshot(TaskEvent event) {
        Task task = tasks.findById(event.taskId()).orElseThrow(() -> new TaskNotFoundException(event.taskId()));
        AgentExecution latestWork = executions.findByTaskId(task.id()).stream()
                .filter(execution -> execution.agentType().isWorker())
                .reduce((first, second) -> second)
                .orElse(null);
        List<Review> taskReviews = reviews.findByTaskId(task.id());
        TaskCard view = new TaskCard(task, latestWork, taskReviews.isEmpty() ? null : taskReviews.getLast());
        return new Snapshot(view, cards.findByTaskId(task.id()).orElse(null));
    }

    private void remember(TaskEvent trigger, BoardCard card, String details) {
        transactions.executeWithoutResult(status -> {
            cards.save(trigger.taskId(), card);
            recorder.record(TaskEvent.of(trigger.taskId(), TaskEventType.TRELLO_UPDATED, TaskEvent.SYSTEM, details,
                    clock.instant()));
        });
    }

    static Optional<String> comment(TaskEvent event) {
        String who = event.agentActor().map(TaskBoardSynchronizer::displayName).orElse("System");
        String details = event.details() == null ? "" : shorten(event.details());
        return Optional.ofNullable(switch (event.type()) {
            case TASK_ASSIGNED -> "🧭 Manager: " + details;
            case EXECUTION_STARTED -> "🤖 %s started working (%s)".formatted(who, details);
            case EXECUTION_COMPLETED -> "✅ %s finished: %s".formatted(who, details);
            case EXECUTION_FAILED -> "⚠️ %s failed: %s".formatted(who, details);
            case CHANGES_REQUESTED -> "🔁 Reviewer requested changes: " + details;
            case REVIEW_APPROVED -> "👍 Reviewer approved: " + details;
            case HUMAN_APPROVAL_REQUESTED -> "🙋 Waiting for human approval";
            case TASK_BLOCKED -> "⛔ Blocked: " + details;
            case TASK_RESUMED -> "▶️ Resumed";
            case TASK_COMPLETED -> "🏁 Completed (" + details + ")";
            case TASK_FAILED -> "❌ Failed: " + details;
            case TASK_CANCELLED -> "🚫 Cancelled";
            default -> null;
        });
    }

    private static String displayName(AgentType type) {
        String name = type.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static String shorten(String text) {
        return text.length() <= MAX_COMMENT_DETAILS ? text : text.substring(0, MAX_COMMENT_DETAILS) + "…";
    }

    private record Snapshot(TaskCard view, BoardCard card) {
    }
}

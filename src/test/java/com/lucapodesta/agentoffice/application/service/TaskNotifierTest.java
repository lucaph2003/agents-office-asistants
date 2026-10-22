package com.lucapodesta.agentoffice.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.lucapodesta.agentoffice.application.port.out.BoardCard;
import com.lucapodesta.agentoffice.application.port.out.TaskNotification;
import com.lucapodesta.agentoffice.application.support.InMemoryStore;
import com.lucapodesta.agentoffice.domain.agent.AgentType;
import com.lucapodesta.agentoffice.domain.agent.ManagerDecision;
import com.lucapodesta.agentoffice.domain.exception.NotificationException;
import com.lucapodesta.agentoffice.domain.execution.AgentExecution;
import com.lucapodesta.agentoffice.domain.review.Review;
import com.lucapodesta.agentoffice.domain.review.ReviewStatus;
import com.lucapodesta.agentoffice.domain.review.Reviewer;
import com.lucapodesta.agentoffice.domain.task.Task;
import com.lucapodesta.agentoffice.domain.task.TaskEvent;
import com.lucapodesta.agentoffice.domain.task.TaskEventType;
import com.lucapodesta.agentoffice.domain.task.TaskPriority;
import com.lucapodesta.agentoffice.domain.task.TaskSource;
import com.lucapodesta.agentoffice.domain.task.TaskStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

class TaskNotifierTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private final InMemoryStore store = new InMemoryStore();
    private final List<TaskNotification> sent = new ArrayList<>();
    private RuntimeException failure;
    private final TaskNotifier notifier = new TaskNotifier(notification -> {
        if (failure != null) {
            throw failure;
        }
        sent.add(notification);
    }, store.taskRepository, store.executionRepository, store.reviewRepository, store.boardCardRepository,
            new TaskEventRecorder(store.eventRepository, event -> { }), TransactionOperations.withoutTransaction(),
            Clock.fixed(NOW, ZoneOffset.UTC));

    private final Task task = Task.create("Java 21 plan", "Plan it", TaskPriority.MEDIUM, TaskSource.TELEGRAM,
            "chat-42", false, NOW);

    @Test
    void completedTaskCarriesResultForTheUser() {
        completeTask();
        store.boardCardRepository.save(task.id(), new BoardCard("c1", "https://trello.com/c/c1", TaskStatus.DONE));

        notifier.onEvent(event(TaskEventType.TASK_COMPLETED, "agent=DEVELOPER attempts=1"));

        assertThat(sent).singleElement().satisfies(notification -> {
            assertThat(notification.event()).isEqualTo(TaskEventType.TASK_COMPLETED);
            assertThat(notification.status()).isEqualTo(TaskStatus.DONE);
            assertThat(notification.agent()).isEqualTo(AgentType.DEVELOPER);
            assertThat(notification.review()).isEqualTo(ReviewStatus.APPROVED);
            assertThat(notification.summary()).isEqualTo("Three-phase plan");
            assertThat(notification.reason()).isNull();
            assertThat(notification.boardUrl()).isEqualTo("https://trello.com/c/c1");
            assertThat(notification.externalReference()).isEqualTo("chat-42");
        });
        assertThat(store.eventsOf(task.id())).extracting(TaskEvent::type, TaskEvent::details)
                .containsExactly(tuple(TaskEventType.USER_NOTIFIED, "event=TASK_COMPLETED"));
    }

    @Test
    void blockedTaskExplainsWhy() {
        store.taskRepository.save(task);

        notifier.onEvent(event(TaskEventType.TASK_BLOCKED, "OLLAMA_UNAVAILABLE: connection refused"));

        assertThat(sent).singleElement().extracting(TaskNotification::reason)
                .isEqualTo("OLLAMA_UNAVAILABLE: connection refused");
    }

    @Test
    void intermediateStepsAreNotNotified() {
        store.taskRepository.save(task);

        notifier.onEvent(event(TaskEventType.TASK_ASSIGNED, "Assigned"));
        notifier.onEvent(event(TaskEventType.REVIEW_APPROVED, "ok"));
        notifier.onEvent(event(TaskEventType.USER_NOTIFIED, "event=TASK_COMPLETED"));

        assertThat(sent).isEmpty();
        assertThat(store.eventsOf(task.id())).isEmpty();
    }

    @Test
    void deliveryFailureIsRecorded() {
        store.taskRepository.save(task);
        failure = new NotificationException("n8n webhook answered with status 500", null);

        notifier.onEvent(event(TaskEventType.TASK_FAILED, "Rejected"));

        assertThat(store.eventsOf(task.id())).singleElement().satisfies(recorded -> {
            assertThat(recorded.type()).isEqualTo(TaskEventType.NOTIFICATION_FAILED);
            assertThat(recorded.details()).isEqualTo("after TASK_FAILED: n8n webhook answered with status 500");
        });
    }

    private void completeTask() {
        task.assign(new ManagerDecision(AgentType.DEVELOPER, TaskPriority.HIGH, "technical"), NOW);
        task.start(NOW);
        task.submitForReview(NOW);
        task.complete(NOW);
        store.taskRepository.save(task);
        AgentExecution work = AgentExecution.create(task.id(), AgentType.DEVELOPER, 1, "m", "v1", NOW);
        work.start(NOW);
        work.succeed("# Plan", "Three-phase plan", NOW);
        store.executionRepository.save(work);
        store.reviewRepository.save(Review.of(task.id(), work.id(), Reviewer.AGENT, ReviewStatus.APPROVED, null, NOW));
    }

    private TaskEvent event(TaskEventType type, String details) {
        return TaskEvent.of(task.id(), type, TaskEvent.SYSTEM, details, NOW);
    }
}

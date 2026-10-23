package com.lucapodesta.agentoffice.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.lucapodesta.agentoffice.AbstractIntegrationTest;
import com.lucapodesta.agentoffice.application.port.out.AgentExecutionRepository;
import com.lucapodesta.agentoffice.application.port.out.BoardCard;
import com.lucapodesta.agentoffice.application.port.out.BoardCardRepository;
import com.lucapodesta.agentoffice.application.port.out.ReviewRepository;
import com.lucapodesta.agentoffice.application.port.out.TaskEventRepository;
import com.lucapodesta.agentoffice.application.port.out.TaskRepository;
import com.lucapodesta.agentoffice.domain.agent.AgentType;
import com.lucapodesta.agentoffice.domain.agent.ManagerDecision;
import com.lucapodesta.agentoffice.domain.exception.ErrorCode;
import com.lucapodesta.agentoffice.domain.execution.AgentExecution;
import com.lucapodesta.agentoffice.domain.execution.ExecutionStatus;
import com.lucapodesta.agentoffice.domain.review.Review;
import com.lucapodesta.agentoffice.domain.review.ReviewStatus;
import com.lucapodesta.agentoffice.domain.review.Reviewer;
import com.lucapodesta.agentoffice.domain.task.Task;
import com.lucapodesta.agentoffice.domain.task.TaskEvent;
import com.lucapodesta.agentoffice.domain.task.TaskEventType;
import com.lucapodesta.agentoffice.domain.task.TaskPriority;
import com.lucapodesta.agentoffice.domain.task.TaskSource;
import com.lucapodesta.agentoffice.domain.task.TaskStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

class PersistenceAdaptersIT extends AbstractIntegrationTest {

    /** PostgreSQL stores microseconds; keep test instants at that precision. */
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @Autowired
    private TaskRepository tasks;
    @Autowired
    private AgentExecutionRepository executions;
    @Autowired
    private ReviewRepository reviews;
    @Autowired
    private TaskEventRepository events;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private BoardCardRepository boardCards;

    @Test
    void taskStateSurvivesRoundTrip() {
        Task task = Task.create("Title", "Description", TaskPriority.LOW, TaskSource.TELEGRAM, "chat-1", true, NOW);
        tasks.save(task);
        task.assign(new ManagerDecision(AgentType.DEVELOPER, TaskPriority.URGENT, "technical"), NOW.plusSeconds(1));
        task.start(NOW.plusSeconds(2));
        tasks.save(task);

        Task loaded = tasks.findById(task.id()).orElseThrow();

        assertThat(loaded).usingRecursiveComparison().isEqualTo(task);
        assertThat(loaded.status()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(loaded.attempt()).isEqualTo(1);
    }

    @Test
    void executionReviewAndEventsAreStoredInOrder() {
        Task task = tasks.save(Task.create("Title", "Description", null, TaskSource.API, null, false, NOW));
        AgentExecution failed = AgentExecution.create(task.id(), AgentType.DEVELOPER, 1, "qwen3", "developer-v1", NOW);
        failed.start(NOW);
        failed.fail(ErrorCode.OLLAMA_UNAVAILABLE, NOW.plusSeconds(1));
        executions.save(failed);
        AgentExecution succeeded = AgentExecution.create(task.id(), AgentType.DEVELOPER, 2, "qwen3", "developer-v1",
                NOW.plusSeconds(2));
        executions.save(succeeded);
        succeeded.start(NOW.plusSeconds(2));
        succeeded.succeed("A migration plan", null, NOW.plusSeconds(3));
        executions.save(succeeded);
        reviews.save(Review.of(task.id(), succeeded.id(), Reviewer.AGENT, ReviewStatus.CHANGES_REQUESTED,
                "Add a rollback section", NOW.plusSeconds(4)));
        events.append(TaskEvent.of(task.id(), TaskEventType.TASK_CREATED, TaskEvent.USER, null, NOW));
        events.append(TaskEvent.of(task.id(), TaskEventType.EXECUTION_STARTED, TaskEvent.agent(AgentType.DEVELOPER),
                "attempt=1", NOW.plusSeconds(1)));

        assertThat(executions.findByTaskId(task.id()))
                .extracting(AgentExecution::attempt, AgentExecution::status, AgentExecution::errorCode, AgentExecution::output)
                .containsExactly(
                        tuple(1, ExecutionStatus.FAILED, ErrorCode.OLLAMA_UNAVAILABLE, null),
                        tuple(2, ExecutionStatus.SUCCESS, null, "A migration plan"));
        assertThat(reviews.findByTaskId(task.id())).singleElement()
                .satisfies(review -> {
                    assertThat(review.executionId()).isEqualTo(succeeded.id());
                    assertThat(review.feedback()).isEqualTo("Add a rollback section");
                });
        assertThat(events.findByTaskId(task.id()))
                .extracting(TaskEvent::type, TaskEvent::actor)
                .containsExactly(
                        tuple(TaskEventType.TASK_CREATED, "user"),
                        tuple(TaskEventType.EXECUTION_STARTED, "agent:developer"));
    }

    @Test
    void knowsWhichCardsAreAlreadyLinked() {
        Task task = tasks.save(Task.create("Title", "Description", null, TaskSource.TRELLO, "card-1", false, NOW));
        boardCards.save(task.id(), new BoardCard("card-1", "https://trello.com/c/1", TaskStatus.INBOX));

        assertThat(boardCards.linkedCardIds(List.of("card-1", "card-2"))).containsExactly("card-1");
        assertThat(boardCards.linkedCardIds(List.of())).isEmpty();
    }

    @Test
    void concurrentUpdateOfSameTaskIsRejected() {
        Task task = tasks.save(Task.create("Title", "Description", null, TaskSource.API, null, false, NOW));
        TransactionTemplate outer = new TransactionTemplate(transactionManager);
        TransactionTemplate competing = new TransactionTemplate(transactionManager);
        competing.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThatThrownBy(() -> outer.executeWithoutResult(status -> {
            Task stale = tasks.findById(task.id()).orElseThrow();
            competing.executeWithoutResult(inner -> {
                Task fresh = tasks.findById(task.id()).orElseThrow();
                fresh.cancel(NOW.plusSeconds(1));
                tasks.save(fresh);
            });
            stale.assign(new ManagerDecision(AgentType.DEVELOPER, TaskPriority.HIGH, "technical"), NOW.plusSeconds(2));
            tasks.save(stale);
        })).isInstanceOf(OptimisticLockingFailureException.class);

        assertThat(tasks.findById(task.id()).orElseThrow().status()).isEqualTo(TaskStatus.CANCELLED);
    }
}

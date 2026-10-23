package com.lucapodesta.agentoffice.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.lucapodesta.agentoffice.application.port.out.BoardCard;
import com.lucapodesta.agentoffice.application.port.out.InboxCard;
import com.lucapodesta.agentoffice.application.port.out.TaskBoardPort;
import com.lucapodesta.agentoffice.application.port.out.TaskCard;
import com.lucapodesta.agentoffice.application.support.InMemoryStore;
import com.lucapodesta.agentoffice.domain.agent.AgentType;
import com.lucapodesta.agentoffice.domain.agent.ManagerDecision;
import com.lucapodesta.agentoffice.domain.exception.TaskBoardException;
import com.lucapodesta.agentoffice.domain.execution.AgentExecution;
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

class TaskBoardSynchronizerTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private final InMemoryStore store = new InMemoryStore();
    private final FakeBoard board = new FakeBoard();
    private final List<Object> published = new ArrayList<>();
    private final TaskBoardSynchronizer synchronizer = new TaskBoardSynchronizer(board, store.boardCardRepository,
            store.taskRepository, store.executionRepository, store.reviewRepository,
            new TaskEventRecorder(store.eventRepository, published::add), TransactionOperations.withoutTransaction(),
            Clock.fixed(NOW, ZoneOffset.UTC));

    private final Task task = Task.create("Java 21 plan", "Plan it", TaskPriority.MEDIUM, TaskSource.TELEGRAM,
            "chat-1", false, NOW);

    @Test
    void createsCardForANewTask() {
        store.taskRepository.save(task);

        synchronizer.onEvent(event(TaskEventType.TASK_CREATED, TaskEvent.USER, null));

        assertThat(board.calls).containsExactly("create INBOX");
        assertThat(store.boardCardRepository.findByTaskId(task.id()))
                .contains(new BoardCard("card-1", "https://trello.test/c/1", TaskStatus.INBOX));
        assertThat(store.eventsOf(task.id())).singleElement().satisfies(recorded -> {
            assertThat(recorded.type()).isEqualTo(TaskEventType.TRELLO_UPDATED);
            assertThat(recorded.details()).isEqualTo("Card created in INBOX");
        });
    }

    @Test
    void movesCardAndCommentsWhenTaskIsAssigned() {
        cardExists(TaskStatus.INBOX);
        task.assign(new ManagerDecision(AgentType.DEVELOPER, TaskPriority.HIGH, "technical"), NOW);
        store.taskRepository.save(task);

        synchronizer.onEvent(event(TaskEventType.TASK_ASSIGNED, TaskEvent.agent(AgentType.MANAGER),
                "Assigned to DEVELOPER (HIGH): technical"));

        assertThat(board.calls).containsExactly("update card-1 PLANNED",
                "comment card-1 🧭 Manager: Assigned to DEVELOPER (HIGH): technical");
        assertThat(store.boardCardRepository.findByTaskId(task.id()).orElseThrow().syncedStatus())
                .isEqualTo(TaskStatus.PLANNED);
        assertThat(store.eventsOf(task.id())).extracting(TaskEvent::details).containsExactly("Card moved to PLANNED");
    }

    @Test
    void refreshesWithoutAuditWhenColumnDoesNotChange() {
        startWork();
        cardExists(TaskStatus.IN_PROGRESS);
        AgentExecution work = AgentExecution.create(task.id(), AgentType.DEVELOPER, 1, "m", "v1", NOW);
        work.start(NOW);
        work.succeed("# Plan", "A three-step plan", NOW);
        store.executionRepository.save(work);

        synchronizer.onEvent(event(TaskEventType.EXECUTION_COMPLETED, TaskEvent.agent(AgentType.DEVELOPER),
                "A three-step plan"));

        assertThat(board.calls).containsExactly("update card-1 IN_PROGRESS",
                "comment card-1 ✅ Developer finished: A three-step plan");
        assertThat(board.lastCard.latestWork().summary()).isEqualTo("A three-step plan");
        assertThat(store.eventsOf(task.id())).isEmpty();
    }

    @Test
    void completedTaskGetsItsFullResultAsAComment() {
        startWork();
        recordWork("# 10 short video ideas\n1. /init and CLAUDE.md\n2. Plan mode");
        task.submitForReview(NOW);
        task.complete(NOW);
        store.taskRepository.save(task);
        cardExists(TaskStatus.REVIEW);

        synchronizer.onEvent(event(TaskEventType.TASK_COMPLETED, TaskEvent.SYSTEM, "agent=DEVELOPER attempts=1"));

        assertThat(board.calls).containsExactly(
                "update card-1 DONE",
                "comment card-1 🏁 Completed (agent=DEVELOPER attempts=1)",
                "comment card-1 📄 **Result** (Developer)\n\n# 10 short video ideas\n1. /init and CLAUDE.md\n2. Plan mode");
    }

    @Test
    void veryLongResultsAreCutToFitTrello() {
        startWork();
        recordWork("x".repeat(TaskBoardSynchronizer.MAX_RESULT_LENGTH + 5_000));
        task.submitForReview(NOW);
        task.complete(NOW);
        store.taskRepository.save(task);

        String result = TaskBoardSynchronizer.resultComment(
                event(TaskEventType.TASK_COMPLETED, TaskEvent.SYSTEM, "done"),
                new TaskCard(task, store.executionsOf(task.id()).getLast(), null)).orElseThrow();

        assertThat(result.length()).isLessThan(16_384);
        assertThat(result).endsWith("full result in GET /api/v1/tasks/" + task.id() + "/executions)");
    }

    @Test
    void noResultCommentBeforeCompletionOrWithoutOutput() {
        startWork();
        recordWork("# Plan");

        assertThat(TaskBoardSynchronizer.resultComment(event(TaskEventType.REVIEW_APPROVED, TaskEvent.SYSTEM, "ok"),
                new TaskCard(task, store.executionsOf(task.id()).getLast(), null))).isEmpty();
        assertThat(TaskBoardSynchronizer.resultComment(event(TaskEventType.TASK_COMPLETED, TaskEvent.SYSTEM, "ok"),
                new TaskCard(task, null, null))).isEmpty();
    }

    @Test
    void ignoresInternalStepsAndItsOwnEvents() {
        cardExists(TaskStatus.INBOX);
        store.taskRepository.save(task);

        synchronizer.onEvent(event(TaskEventType.EXECUTION_STARTED, TaskEvent.agent(AgentType.MANAGER), "attempt=1"));
        synchronizer.onEvent(event(TaskEventType.EXECUTION_COMPLETED, TaskEvent.agent(AgentType.REVIEWER), "ok"));
        synchronizer.onEvent(event(TaskEventType.TRELLO_UPDATED, TaskEvent.SYSTEM, "Card moved"));
        synchronizer.onEvent(event(TaskEventType.TRELLO_SYNC_FAILED, TaskEvent.SYSTEM, "boom"));

        assertThat(board.calls).isEmpty();
    }

    @Test
    void boardFailureIsRecordedAndRepairedByTheNextEvent() {
        store.taskRepository.save(task);
        board.failNext = true;

        synchronizer.onEvent(event(TaskEventType.TASK_CREATED, TaskEvent.USER, null));

        assertThat(store.boardCardRepository.findByTaskId(task.id())).isEmpty();
        assertThat(store.eventsOf(task.id())).singleElement().satisfies(recorded -> {
            assertThat(recorded.type()).isEqualTo(TaskEventType.TRELLO_SYNC_FAILED);
            assertThat(recorded.details()).isEqualTo("after TASK_CREATED: Trello create card failed with status 503");
        });

        task.assign(new ManagerDecision(AgentType.RESEARCH, TaskPriority.LOW, "analysis"), NOW);
        store.taskRepository.save(task);
        synchronizer.onEvent(event(TaskEventType.TASK_ASSIGNED, TaskEvent.agent(AgentType.MANAGER), "Assigned"));

        assertThat(board.calls).containsExactly("create PLANNED", "comment card-1 🧭 Manager: Assigned");
        assertThat(store.boardCardRepository.findByTaskId(task.id())).isPresent();
    }

    @Test
    void commentsDescribeEachStep() {
        assertThat(TaskBoardSynchronizer.comment(event(TaskEventType.CHANGES_REQUESTED, TaskEvent.agent(AgentType.REVIEWER), "Add tests")))
                .contains("🔁 Reviewer requested changes: Add tests");
        assertThat(TaskBoardSynchronizer.comment(event(TaskEventType.TASK_BLOCKED, TaskEvent.SYSTEM, "OLLAMA_UNAVAILABLE: down")))
                .contains("⛔ Blocked: OLLAMA_UNAVAILABLE: down");
        assertThat(TaskBoardSynchronizer.comment(event(TaskEventType.EXECUTION_STARTED, TaskEvent.agent(AgentType.RESEARCH), "attempt=2")))
                .contains("🤖 Research started working (attempt=2)");
        assertThat(TaskBoardSynchronizer.comment(event(TaskEventType.TASK_FAILED, TaskEvent.SYSTEM, "x".repeat(800))).orElseThrow())
                .hasSizeLessThan(520);
        assertThat(TaskBoardSynchronizer.comment(event(TaskEventType.TASK_CREATED, TaskEvent.USER, null))).isEmpty();
    }

    private void startWork() {
        task.assign(new ManagerDecision(AgentType.DEVELOPER, TaskPriority.HIGH, "technical"), NOW);
        task.start(NOW);
        store.taskRepository.save(task);
    }

    private void recordWork(String output) {
        AgentExecution work = AgentExecution.create(task.id(), AgentType.DEVELOPER, 1, "m", "v1", NOW);
        work.start(NOW);
        work.succeed(output, "summary", NOW);
        store.executionRepository.save(work);
    }

    private void cardExists(TaskStatus status) {
        store.boardCardRepository.save(task.id(), new BoardCard("card-1", "https://trello.test/c/1", status));
    }

    private TaskEvent event(TaskEventType type, String actor, String details) {
        return TaskEvent.of(task.id(), type, actor, details, NOW);
    }

    private static class FakeBoard implements TaskBoardPort {

        final List<String> calls = new ArrayList<>();
        TaskCard lastCard;
        boolean failNext;

        @Override
        public BoardCard createCard(TaskCard card) {
            failIfAsked("create card");
            calls.add("create " + card.task().status());
            lastCard = card;
            return new BoardCard("card-1", "https://trello.test/c/1", card.task().status());
        }

        @Override
        public void updateCard(String cardId, TaskCard card) {
            failIfAsked("update card");
            calls.add("update " + cardId + " " + card.task().status());
            lastCard = card;
        }

        @Override
        public void addComment(String cardId, String text) {
            calls.add("comment " + cardId + " " + text);
        }

        @Override
        public List<InboxCard> inboxCards() {
            return List.of();
        }

        private void failIfAsked(String operation) {
            if (failNext) {
                failNext = false;
                throw new TaskBoardException("Trello " + operation + " failed with status 503", null);
            }
        }
    }
}

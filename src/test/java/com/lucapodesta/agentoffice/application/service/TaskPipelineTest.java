package com.lucapodesta.agentoffice.application.service;

import static com.lucapodesta.agentoffice.domain.task.TaskEventType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.lucapodesta.agentoffice.application.agent.AgentTeam;
import com.lucapodesta.agentoffice.application.agent.ManagerAgent;
import com.lucapodesta.agentoffice.application.agent.ReviewerAgent;
import com.lucapodesta.agentoffice.application.agent.WorkerAgent;
import com.lucapodesta.agentoffice.application.support.InMemoryStore;
import com.lucapodesta.agentoffice.application.support.ScriptedLanguageModel;
import com.lucapodesta.agentoffice.domain.agent.AgentType;
import com.lucapodesta.agentoffice.domain.exception.ErrorCode;
import com.lucapodesta.agentoffice.domain.exception.LanguageModelException;
import com.lucapodesta.agentoffice.domain.execution.AgentExecution;
import com.lucapodesta.agentoffice.domain.execution.ExecutionStatus;
import com.lucapodesta.agentoffice.domain.review.Review;
import com.lucapodesta.agentoffice.domain.review.ReviewPolicy;
import com.lucapodesta.agentoffice.domain.review.ReviewStatus;
import com.lucapodesta.agentoffice.domain.task.Task;
import com.lucapodesta.agentoffice.domain.task.TaskPriority;
import com.lucapodesta.agentoffice.domain.task.TaskSource;
import com.lucapodesta.agentoffice.domain.task.TaskStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

class TaskPipelineTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final String ROUTE_TO_DEVELOPER =
            "{\"agent\": \"DEVELOPER\", \"priority\": \"HIGH\", \"reason\": \"Technical plan needed\"}";
    private static final String DELIVERABLE =
            "{\"summary\": \"Three-phase migration plan\", \"content\": \"# Plan\\n1. Upgrade build\"}";
    private static final String APPROVED = "{\"verdict\": \"APPROVED\", \"feedback\": \"Complete and actionable\"}";
    private static final String CHANGES = "{\"verdict\": \"CHANGES_REQUESTED\", \"feedback\": \"Add a rollback plan\"}";

    private final InMemoryStore store = new InMemoryStore();
    private final ScriptedLanguageModel model = new ScriptedLanguageModel();
    private final TaskPipeline pipeline = new TaskPipeline(
            store.taskRepository, store.executionRepository, store.reviewRepository, store.eventRepository,
            new AgentTeam(
                    new ManagerAgent(model, ScriptedLanguageModel.PROMPTS),
                    List.of(new WorkerAgent(AgentType.RESEARCH, model, ScriptedLanguageModel.PROMPTS),
                            new WorkerAgent(AgentType.DEVELOPER, model, ScriptedLanguageModel.PROMPTS)),
                    new ReviewerAgent(model, ScriptedLanguageModel.PROMPTS)),
            new ReviewPolicy(3), 2, TransactionOperations.withoutTransaction(),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void approvedWorkCompletesTheTask() {
        model.answer(AgentType.MANAGER, ROUTE_TO_DEVELOPER)
                .answer(AgentType.DEVELOPER, DELIVERABLE)
                .answer(AgentType.REVIEWER, APPROVED);
        UUID id = newTask(false);

        pipeline.process(id);

        Task task = store.task(id);
        assertThat(task.status()).isEqualTo(TaskStatus.DONE);
        assertThat(task.assignedAgent()).isEqualTo(AgentType.DEVELOPER);
        assertThat(task.priority()).isEqualTo(TaskPriority.HIGH);
        assertThat(task.attempt()).isEqualTo(1);
        assertThat(store.executionsOf(id))
                .extracting(AgentExecution::agentType, AgentExecution::status, AgentExecution::promptVersion)
                .containsExactly(
                        tuple(AgentType.MANAGER, ExecutionStatus.SUCCESS, "manager-test"),
                        tuple(AgentType.DEVELOPER, ExecutionStatus.SUCCESS, "developer-test"),
                        tuple(AgentType.REVIEWER, ExecutionStatus.SUCCESS, "reviewer-test"));
        assertThat(store.executionsOf(id).get(1).summary()).isEqualTo("Three-phase migration plan");
        assertThat(store.executionsOf(id).get(1).output()).isEqualTo("# Plan\n1. Upgrade build");
        assertThat(store.reviewsOf(id)).singleElement()
                .extracting(Review::status, Review::executionId)
                .containsExactly(ReviewStatus.APPROVED, store.executionsOf(id).get(1).id());
        assertThat(store.eventTypesOf(id)).containsExactly(
                EXECUTION_STARTED, EXECUTION_COMPLETED, TASK_ASSIGNED,
                EXECUTION_STARTED, EXECUTION_COMPLETED, REVIEW_STARTED,
                EXECUTION_STARTED, EXECUTION_COMPLETED, REVIEW_APPROVED, TASK_COMPLETED);
    }

    @Test
    void requestedChangesTriggerAnotherAttemptWithFeedback() {
        model.answer(AgentType.MANAGER, ROUTE_TO_DEVELOPER)
                .answer(AgentType.DEVELOPER, DELIVERABLE, DELIVERABLE)
                .answer(AgentType.REVIEWER, CHANGES, APPROVED);
        UUID id = newTask(false);

        pipeline.process(id);

        assertThat(store.task(id).status()).isEqualTo(TaskStatus.DONE);
        assertThat(store.task(id).attempt()).isEqualTo(2);
        assertThat(workerExecutions(id))
                .extracting(AgentExecution::attempt, AgentExecution::status)
                .containsExactly(tuple(1, ExecutionStatus.REJECTED), tuple(2, ExecutionStatus.SUCCESS));
        assertThat(model.requestsFor(AgentType.DEVELOPER).get(1).userPrompt())
                .contains("<review_feedback>\nAdd a rollback plan")
                .contains("<previous_output>\n# Plan");
        assertThat(store.eventTypesOf(id)).contains(CHANGES_REQUESTED, TASK_COMPLETED);
    }

    @Test
    void reviewLoopStopsAtMaxAttempts() {
        model.answer(AgentType.MANAGER, ROUTE_TO_DEVELOPER)
                .answer(AgentType.DEVELOPER, DELIVERABLE, DELIVERABLE, DELIVERABLE)
                .answer(AgentType.REVIEWER, CHANGES, CHANGES, CHANGES);
        UUID id = newTask(false);

        pipeline.process(id);

        assertThat(store.task(id).status()).isEqualTo(TaskStatus.FAILED);
        assertThat(workerExecutions(id)).hasSize(3).allMatch(e -> e.status() == ExecutionStatus.REJECTED);
        assertThat(store.eventsOf(id).getLast().details()).isEqualTo("MAX_RETRIES_EXCEEDED after 3 attempts");
    }

    @Test
    void rejectionFailsTheTaskWithoutRetrying() {
        model.answer(AgentType.MANAGER, ROUTE_TO_DEVELOPER)
                .answer(AgentType.DEVELOPER, DELIVERABLE)
                .answer(AgentType.REVIEWER, "{\"verdict\": \"REJECTED\", \"feedback\": \"Off topic\"}");
        UUID id = newTask(false);

        pipeline.process(id);

        assertThat(store.task(id).status()).isEqualTo(TaskStatus.FAILED);
        assertThat(model.requestsFor(AgentType.DEVELOPER)).hasSize(1);
        assertThat(store.eventsOf(id).getLast().details()).isEqualTo("Rejected by reviewer: Off topic");
    }

    @Test
    void humanApprovalKeepsTaskInReview() {
        model.answer(AgentType.MANAGER, ROUTE_TO_DEVELOPER)
                .answer(AgentType.DEVELOPER, DELIVERABLE)
                .answer(AgentType.REVIEWER, APPROVED);
        UUID id = newTask(true);

        pipeline.process(id);
        pipeline.process(id);

        assertThat(store.task(id).status()).isEqualTo(TaskStatus.REVIEW);
        assertThat(store.eventTypesOf(id)).endsWith(REVIEW_APPROVED, HUMAN_APPROVAL_REQUESTED);
        assertThat(model.requestsFor(AgentType.REVIEWER)).hasSize(1);
    }

    @Test
    void unavailableModelBlocksTaskAndResumeContinuesIt() {
        model.answer(AgentType.MANAGER, ROUTE_TO_DEVELOPER)
                .fail(AgentType.DEVELOPER, new LanguageModelException(ErrorCode.OLLAMA_UNAVAILABLE, "connection refused"))
                .answer(AgentType.DEVELOPER, DELIVERABLE)
                .answer(AgentType.REVIEWER, APPROVED);
        UUID id = newTask(false);

        pipeline.process(id);

        assertThat(store.task(id).status()).isEqualTo(TaskStatus.BLOCKED);
        assertThat(workerExecutions(id)).singleElement()
                .extracting(AgentExecution::status, AgentExecution::errorCode)
                .containsExactly(ExecutionStatus.FAILED, ErrorCode.OLLAMA_UNAVAILABLE);
        assertThat(store.eventsOf(id).getLast().details()).isEqualTo("OLLAMA_UNAVAILABLE: connection refused");

        Task blocked = store.task(id);
        blocked.resume(NOW);
        store.taskRepository.save(blocked);
        pipeline.process(id);

        assertThat(store.task(id).status()).isEqualTo(TaskStatus.DONE);
        assertThat(store.task(id).attempt()).isEqualTo(2);
        assertThat(model.requestsFor(AgentType.MANAGER)).hasSize(1);
    }

    @Test
    void unusableOutputIsRetriedOnce() {
        model.answer(AgentType.MANAGER, "Sure! I think a developer should do it.", ROUTE_TO_DEVELOPER)
                .answer(AgentType.DEVELOPER, DELIVERABLE)
                .answer(AgentType.REVIEWER, APPROVED);
        UUID id = newTask(false);

        pipeline.process(id);

        assertThat(store.task(id).status()).isEqualTo(TaskStatus.DONE);
        assertThat(store.executionsOf(id).getFirst())
                .extracting(AgentExecution::status, AgentExecution::errorCode)
                .containsExactly(ExecutionStatus.FAILED, ErrorCode.INVALID_AGENT_OUTPUT);
    }

    @Test
    void repeatedlyUnusableOutputFailsTheTask() {
        model.answer(AgentType.MANAGER, "no json", "{\"agent\": \"REVIEWER\", \"reason\": \"x\"}");
        UUID id = newTask(false);

        pipeline.process(id);

        assertThat(store.task(id).status()).isEqualTo(TaskStatus.FAILED);
        assertThat(store.executionsOf(id)).hasSize(2).allMatch(e -> e.errorCode() == ErrorCode.INVALID_AGENT_OUTPUT);
        assertThat(store.eventsOf(id).getLast().details()).startsWith("INVALID_AGENT_OUTPUT: Manager can only assign");
    }

    @Test
    void cancellationDuringWorkStopsThePipeline() {
        UUID id = newTask(false);
        model.answer(AgentType.MANAGER, ROUTE_TO_DEVELOPER)
                .answerAfter(AgentType.DEVELOPER, () -> {
                    Task task = store.task(id);
                    task.cancel(NOW);
                    store.taskRepository.save(task);
                }, DELIVERABLE);

        pipeline.process(id);

        assertThat(store.task(id).status()).isEqualTo(TaskStatus.CANCELLED);
        assertThat(workerExecutions(id)).singleElement().extracting(AgentExecution::status)
                .isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(model.requestsFor(AgentType.REVIEWER)).isEmpty();
    }

    @Test
    void researchTasksGoToTheResearchAgent() {
        model.answer(AgentType.MANAGER, "{\"agent\": \"research\", \"priority\": \"low\", \"reason\": \"Analysis\"}")
                .answer(AgentType.RESEARCH, DELIVERABLE)
                .answer(AgentType.REVIEWER, APPROVED);
        UUID id = newTask(false);

        pipeline.process(id);

        assertThat(store.task(id).status()).isEqualTo(TaskStatus.DONE);
        assertThat(store.task(id).assignedAgent()).isEqualTo(AgentType.RESEARCH);
        assertThat(model.requestsFor(AgentType.RESEARCH)).hasSize(1);
    }

    @Test
    void finishedTasksAreLeftAlone() {
        UUID id = newTask(false);
        Task task = store.task(id);
        task.cancel(NOW);
        store.taskRepository.save(task);

        pipeline.process(id);

        assertThat(store.executionsOf(id)).isEmpty();
    }

    private UUID newTask(boolean requiresHumanApproval) {
        Task task = Task.create("Java 21 migration", "Plan the migration of a Spring Boot 2 service",
                TaskPriority.MEDIUM, TaskSource.TELEGRAM, "chat-1", requiresHumanApproval, NOW);
        store.taskRepository.save(task);
        return task.id();
    }

    private List<AgentExecution> workerExecutions(UUID id) {
        return store.executionsOf(id).stream().filter(e -> e.agentType().isWorker()).toList();
    }
}

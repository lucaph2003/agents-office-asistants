package com.lucapodesta.agentoffice.application.support;

import com.lucapodesta.agentoffice.application.dto.PageQuery;
import com.lucapodesta.agentoffice.application.dto.PageResult;
import com.lucapodesta.agentoffice.application.dto.TaskFilter;
import com.lucapodesta.agentoffice.application.port.out.AgentExecutionRepository;
import com.lucapodesta.agentoffice.application.port.out.BoardCard;
import com.lucapodesta.agentoffice.application.port.out.BoardCardRepository;
import com.lucapodesta.agentoffice.application.port.out.ReviewRepository;
import com.lucapodesta.agentoffice.application.port.out.TaskEventRepository;
import com.lucapodesta.agentoffice.application.port.out.TaskRepository;
import com.lucapodesta.agentoffice.domain.execution.AgentExecution;
import com.lucapodesta.agentoffice.domain.review.Review;
import com.lucapodesta.agentoffice.domain.task.Task;
import com.lucapodesta.agentoffice.domain.task.TaskEvent;
import com.lucapodesta.agentoffice.domain.task.TaskEventType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * In-memory implementation of the persistence ports. Stores rebuilt copies so tests cannot
 * accidentally rely on shared object references, just like with a real database.
 */
public class InMemoryStore {

    private final Map<UUID, Task> tasks = new LinkedHashMap<>();
    private final Map<UUID, AgentExecution> executions = new LinkedHashMap<>();
    private final List<Review> reviews = new ArrayList<>();
    private final List<TaskEvent> events = new ArrayList<>();
    private final Map<UUID, BoardCard> boardCards = new LinkedHashMap<>();

    public final TaskRepository taskRepository = new TaskRepository() {
        @Override
        public Task save(Task task) {
            tasks.put(task.id(), copy(task));
            return copy(task);
        }

        @Override
        public Optional<Task> findById(UUID id) {
            return Optional.ofNullable(tasks.get(id)).map(InMemoryStore::copy);
        }

        @Override
        public PageResult<Task> search(TaskFilter filter, PageQuery page) {
            throw new UnsupportedOperationException("not needed by these tests");
        }
    };

    public final AgentExecutionRepository executionRepository = new AgentExecutionRepository() {
        @Override
        public AgentExecution save(AgentExecution execution) {
            executions.put(execution.id(), copy(execution));
            return copy(execution);
        }

        @Override
        public List<AgentExecution> findByTaskId(UUID taskId) {
            return executions.values().stream().filter(e -> e.taskId().equals(taskId)).map(InMemoryStore::copy).toList();
        }
    };

    public final ReviewRepository reviewRepository = new ReviewRepository() {
        @Override
        public Review save(Review review) {
            reviews.add(review);
            return review;
        }

        @Override
        public List<Review> findByTaskId(UUID taskId) {
            return reviews.stream().filter(r -> r.taskId().equals(taskId)).toList();
        }
    };

    public final TaskEventRepository eventRepository = new TaskEventRepository() {
        @Override
        public void append(TaskEvent event) {
            events.add(event);
        }

        @Override
        public List<TaskEvent> findByTaskId(UUID taskId) {
            return events.stream().filter(e -> e.taskId().equals(taskId)).toList();
        }
    };

    public final BoardCardRepository boardCardRepository = new BoardCardRepository() {
        @Override
        public void save(UUID taskId, BoardCard card) {
            boardCards.put(taskId, card);
        }

        @Override
        public Optional<BoardCard> findByTaskId(UUID taskId) {
            return Optional.ofNullable(boardCards.get(taskId));
        }

        @Override
        public Map<UUID, BoardCard> findByTaskIds(Collection<UUID> taskIds) {
            Map<UUID, BoardCard> found = new LinkedHashMap<>();
            taskIds.forEach(id -> findByTaskId(id).ifPresent(card -> found.put(id, card)));
            return found;
        }

        @Override
        public Set<String> linkedCardIds(Collection<String> cardIds) {
            return boardCards.values().stream().map(BoardCard::id).filter(cardIds::contains).collect(Collectors.toSet());
        }
    };

    public Task task(UUID id) {
        return taskRepository.findById(id).orElseThrow();
    }

    public List<AgentExecution> executionsOf(UUID taskId) {
        return executionRepository.findByTaskId(taskId);
    }

    public List<Review> reviewsOf(UUID taskId) {
        return reviewRepository.findByTaskId(taskId);
    }

    public List<TaskEventType> eventTypesOf(UUID taskId) {
        return eventRepository.findByTaskId(taskId).stream().map(TaskEvent::type).toList();
    }

    public List<TaskEvent> eventsOf(UUID taskId) {
        return eventRepository.findByTaskId(taskId);
    }

    private static Task copy(Task t) {
        return Task.restore(t.id(), t.title(), t.description(), t.status(), t.priority(), t.assignedAgent(),
                t.source(), t.externalReference(), t.requiresHumanApproval(), t.attempt(), t.createdAt(),
                t.updatedAt(), t.completedAt());
    }

    private static AgentExecution copy(AgentExecution e) {
        return AgentExecution.restore(e.id(), e.taskId(), e.agentType(), e.attempt(), e.model(), e.promptVersion(),
                e.status(), e.output(), e.summary(), e.errorCode(), e.createdAt(), e.startedAt(), e.finishedAt());
    }
}

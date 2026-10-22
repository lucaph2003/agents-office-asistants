package com.lucapodesta.agentoffice.application.service;

import com.lucapodesta.agentoffice.application.dto.CreateTaskCommand;
import com.lucapodesta.agentoffice.application.dto.PageQuery;
import com.lucapodesta.agentoffice.application.dto.PageResult;
import com.lucapodesta.agentoffice.application.dto.TaskFilter;
import com.lucapodesta.agentoffice.application.dto.TaskExecutionRequestedEvent;
import com.lucapodesta.agentoffice.application.dto.TaskSubmittedEvent;
import com.lucapodesta.agentoffice.application.port.out.AgentExecutionRepository;
import com.lucapodesta.agentoffice.application.port.out.ReviewRepository;
import com.lucapodesta.agentoffice.application.port.out.TaskEventRepository;
import com.lucapodesta.agentoffice.application.port.out.TaskRepository;
import com.lucapodesta.agentoffice.domain.exception.InvalidTaskStateException;
import com.lucapodesta.agentoffice.domain.exception.TaskNotFoundException;
import com.lucapodesta.agentoffice.domain.execution.AgentExecution;
import com.lucapodesta.agentoffice.domain.review.Review;
import com.lucapodesta.agentoffice.domain.task.Task;
import com.lucapodesta.agentoffice.domain.task.TaskEvent;
import com.lucapodesta.agentoffice.domain.task.TaskEventType;
import com.lucapodesta.agentoffice.domain.task.TaskStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private final TaskRepository tasks;
    private final TaskEventRepository events;
    private final AgentExecutionRepository executions;
    private final ReviewRepository reviews;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    public TaskService(TaskRepository tasks, TaskEventRepository events, AgentExecutionRepository executions,
                       ReviewRepository reviews, ApplicationEventPublisher publisher, Clock clock) {
        this.tasks = tasks;
        this.events = events;
        this.executions = executions;
        this.reviews = reviews;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Transactional
    public Task create(CreateTaskCommand command) {
        Instant now = clock.instant();
        Task task = tasks.save(Task.create(command.title(), command.description(), command.priority(),
                command.source(), command.externalReference(), command.requiresHumanApproval(), now));
        events.append(TaskEvent.of(task.id(), TaskEventType.TASK_CREATED, TaskEvent.USER,
                "source=%s priority=%s".formatted(task.source(), task.priority()), now));
        log.info("event=TASK_CREATED taskId={} source={} priority={}", task.id(), task.source(), task.priority());
        publisher.publishEvent(new TaskSubmittedEvent(task.id()));
        return task;
    }

    @Transactional(readOnly = true)
    public Task get(UUID id) {
        return tasks.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public PageResult<Task> list(TaskFilter filter, PageQuery page) {
        return tasks.search(filter, page);
    }

    @Transactional
    public Task cancel(UUID id) {
        Task task = get(id);
        Instant now = clock.instant();
        TaskEvent cancelled = TaskEvent.of(id, TaskEventType.TASK_CANCELLED, TaskEvent.USER,
                "from=" + task.status(), now);
        task.cancel(now);
        Task saved = tasks.save(task);
        events.append(cancelled);
        log.info("event=TASK_CANCELLED taskId={}", id);
        return saved;
    }

    /**
     * Starts a task that is waiting, or resumes a blocked one. The work itself runs asynchronously
     * once this transaction commits.
     */
    @Transactional
    public Task execute(UUID id) {
        Task task = get(id);
        switch (task.status()) {
            case INBOX, PLANNED -> {
            }
            case BLOCKED -> {
                Instant now = clock.instant();
                task.resume(now);
                task = tasks.save(task);
                events.append(TaskEvent.of(id, TaskEventType.TASK_RESUMED, TaskEvent.USER, "to=" + task.status(), now));
            }
            default -> throw new InvalidTaskStateException(id, task.status(), TaskStatus.IN_PROGRESS);
        }
        log.info("event=TASK_EXECUTION_REQUESTED taskId={} status={}", id, task.status());
        publisher.publishEvent(new TaskExecutionRequestedEvent(id));
        return task;
    }

    @Transactional(readOnly = true)
    public List<AgentExecution> executions(UUID id) {
        get(id);
        return executions.findByTaskId(id);
    }

    @Transactional(readOnly = true)
    public List<Review> reviews(UUID id) {
        get(id);
        return reviews.findByTaskId(id);
    }

    @Transactional(readOnly = true)
    public List<TaskEvent> history(UUID id) {
        get(id);
        return events.findByTaskId(id);
    }
}

package com.lucapodesta.agentoffice.application.service;

import com.lucapodesta.agentoffice.application.dto.CreateTaskCommand;
import com.lucapodesta.agentoffice.application.dto.PageQuery;
import com.lucapodesta.agentoffice.application.dto.PageResult;
import com.lucapodesta.agentoffice.application.dto.TaskFilter;
import com.lucapodesta.agentoffice.application.port.out.TaskEventRepository;
import com.lucapodesta.agentoffice.application.port.out.TaskRepository;
import com.lucapodesta.agentoffice.domain.exception.TaskNotFoundException;
import com.lucapodesta.agentoffice.domain.task.Task;
import com.lucapodesta.agentoffice.domain.task.TaskEvent;
import com.lucapodesta.agentoffice.domain.task.TaskEventType;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private final TaskRepository tasks;
    private final TaskEventRepository events;
    private final Clock clock;

    public TaskService(TaskRepository tasks, TaskEventRepository events, Clock clock) {
        this.tasks = tasks;
        this.events = events;
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

    @Transactional(readOnly = true)
    public List<TaskEvent> history(UUID id) {
        get(id);
        return events.findByTaskId(id);
    }
}

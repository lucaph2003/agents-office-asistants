package com.lucapodesta.agentoffice.application.port.out;

import com.lucapodesta.agentoffice.application.dto.PageQuery;
import com.lucapodesta.agentoffice.application.dto.PageResult;
import com.lucapodesta.agentoffice.application.dto.TaskFilter;
import com.lucapodesta.agentoffice.domain.task.Task;
import java.util.Optional;
import java.util.UUID;

public interface TaskRepository {

    Task save(Task task);

    Optional<Task> findById(UUID id);

    PageResult<Task> search(TaskFilter filter, PageQuery page);
}

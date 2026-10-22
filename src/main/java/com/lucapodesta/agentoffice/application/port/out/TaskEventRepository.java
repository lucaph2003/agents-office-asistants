package com.lucapodesta.agentoffice.application.port.out;

import com.lucapodesta.agentoffice.domain.task.TaskEvent;
import java.util.List;
import java.util.UUID;

public interface TaskEventRepository {

    void append(TaskEvent event);

    List<TaskEvent> findByTaskId(UUID taskId);
}

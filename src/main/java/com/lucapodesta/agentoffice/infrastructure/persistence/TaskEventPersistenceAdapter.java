package com.lucapodesta.agentoffice.infrastructure.persistence;

import com.lucapodesta.agentoffice.application.port.out.TaskEventRepository;
import com.lucapodesta.agentoffice.domain.task.TaskEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class TaskEventPersistenceAdapter implements TaskEventRepository {

    private final TaskEventJpaRepository jpa;

    TaskEventPersistenceAdapter(TaskEventJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void append(TaskEvent event) {
        jpa.save(TaskEventEntity.from(event));
    }

    @Override
    public List<TaskEvent> findByTaskId(UUID taskId) {
        return jpa.findByTaskIdOrderBySequenceAsc(taskId).stream().map(TaskEventEntity::toDomain).toList();
    }
}

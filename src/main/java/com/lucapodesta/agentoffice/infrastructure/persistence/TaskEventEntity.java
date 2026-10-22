package com.lucapodesta.agentoffice.infrastructure.persistence;

import com.lucapodesta.agentoffice.domain.task.TaskEvent;
import com.lucapodesta.agentoffice.domain.task.TaskEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "task_events")
class TaskEventEntity extends AssignedIdEntity {

    @Id
    private UUID id;

    private UUID taskId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type")
    private TaskEventType type;

    private String actor;
    private String details;
    private Instant createdAt;

    /** Assigned by the database; defines the order of events that share a timestamp. */
    @Column(insertable = false, updatable = false)
    private Long sequence;

    protected TaskEventEntity() {
    }

    static TaskEventEntity from(TaskEvent event) {
        TaskEventEntity entity = new TaskEventEntity();
        entity.id = event.id();
        entity.taskId = event.taskId();
        entity.type = event.type();
        entity.actor = event.actor();
        entity.details = event.details();
        entity.createdAt = event.createdAt();
        return entity;
    }

    TaskEvent toDomain() {
        return new TaskEvent(id, taskId, type, actor, details, createdAt);
    }

    @Override
    public UUID getId() {
        return id;
    }
}

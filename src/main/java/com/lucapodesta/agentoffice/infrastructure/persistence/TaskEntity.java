package com.lucapodesta.agentoffice.infrastructure.persistence;

import com.lucapodesta.agentoffice.domain.agent.AgentType;
import com.lucapodesta.agentoffice.domain.task.Task;
import com.lucapodesta.agentoffice.domain.task.TaskPriority;
import com.lucapodesta.agentoffice.domain.task.TaskSource;
import com.lucapodesta.agentoffice.domain.task.TaskStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tasks")
class TaskEntity {

    @Id
    private UUID id;

    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    private TaskStatus status;

    @Enumerated(EnumType.STRING)
    private TaskPriority priority;

    @Enumerated(EnumType.STRING)
    private AgentType assignedAgent;

    @Enumerated(EnumType.STRING)
    private TaskSource source;

    private String externalReference;
    private boolean requiresHumanApproval;
    private int attempt;

    /** Null until first persisted, which is how Spring Data tells new entities apart. */
    @Version
    private Long version;

    private Instant createdAt;
    private Instant updatedAt;
    private Instant completedAt;

    protected TaskEntity() {
    }

    static TaskEntity from(Task task) {
        TaskEntity entity = new TaskEntity();
        entity.id = task.id();
        entity.title = task.title();
        entity.description = task.description();
        entity.source = task.source();
        entity.externalReference = task.externalReference();
        entity.requiresHumanApproval = task.requiresHumanApproval();
        entity.createdAt = task.createdAt();
        entity.apply(task);
        return entity;
    }

    /** Copies the mutable state of the task. Immutable fields are never rewritten. */
    void apply(Task task) {
        status = task.status();
        priority = task.priority();
        assignedAgent = task.assignedAgent();
        attempt = task.attempt();
        updatedAt = task.updatedAt();
        completedAt = task.completedAt();
    }

    Task toDomain() {
        return Task.restore(id, title, description, status, priority, assignedAgent, source, externalReference,
                requiresHumanApproval, attempt, createdAt, updatedAt, completedAt);
    }
}

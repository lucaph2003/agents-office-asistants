package com.lucapodesta.agentoffice.infrastructure.persistence;

import com.lucapodesta.agentoffice.domain.agent.AgentType;
import com.lucapodesta.agentoffice.domain.exception.ErrorCode;
import com.lucapodesta.agentoffice.domain.execution.AgentExecution;
import com.lucapodesta.agentoffice.domain.execution.ExecutionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_executions")
class AgentExecutionEntity extends AssignedIdEntity {

    @Id
    private UUID id;

    private UUID taskId;

    @Enumerated(EnumType.STRING)
    private AgentType agentType;

    @Enumerated(EnumType.STRING)
    private ExecutionStatus status;

    private int attempt;
    private String model;
    private String promptVersion;

    @Column(columnDefinition = "text")
    private String output;

    @Enumerated(EnumType.STRING)
    private ErrorCode errorCode;

    private Instant createdAt;
    private Instant startedAt;
    private Instant finishedAt;

    protected AgentExecutionEntity() {
    }

    static AgentExecutionEntity from(AgentExecution execution) {
        AgentExecutionEntity entity = new AgentExecutionEntity();
        entity.id = execution.id();
        entity.taskId = execution.taskId();
        entity.agentType = execution.agentType();
        entity.attempt = execution.attempt();
        entity.model = execution.model();
        entity.promptVersion = execution.promptVersion();
        entity.createdAt = execution.createdAt();
        entity.apply(execution);
        return entity;
    }

    void apply(AgentExecution execution) {
        status = execution.status();
        output = execution.output();
        errorCode = execution.errorCode();
        startedAt = execution.startedAt();
        finishedAt = execution.finishedAt();
    }

    AgentExecution toDomain() {
        return AgentExecution.restore(id, taskId, agentType, attempt, model, promptVersion, status, output,
                errorCode, createdAt, startedAt, finishedAt);
    }

    @Override
    public UUID getId() {
        return id;
    }
}

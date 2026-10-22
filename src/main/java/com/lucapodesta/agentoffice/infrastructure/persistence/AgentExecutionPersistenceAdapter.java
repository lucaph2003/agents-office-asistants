package com.lucapodesta.agentoffice.infrastructure.persistence;

import com.lucapodesta.agentoffice.application.port.out.AgentExecutionRepository;
import com.lucapodesta.agentoffice.domain.execution.AgentExecution;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class AgentExecutionPersistenceAdapter implements AgentExecutionRepository {

    private final AgentExecutionJpaRepository jpa;

    AgentExecutionPersistenceAdapter(AgentExecutionJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public AgentExecution save(AgentExecution execution) {
        AgentExecutionEntity entity = jpa.findById(execution.id())
                .map(existing -> {
                    existing.apply(execution);
                    return existing;
                })
                .orElseGet(() -> AgentExecutionEntity.from(execution));
        return jpa.save(entity).toDomain();
    }

    @Override
    public List<AgentExecution> findByTaskId(UUID taskId) {
        return jpa.findByTaskIdOrderByCreatedAtAsc(taskId).stream().map(AgentExecutionEntity::toDomain).toList();
    }
}

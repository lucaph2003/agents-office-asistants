package com.lucapodesta.agentoffice.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AgentExecutionJpaRepository extends JpaRepository<AgentExecutionEntity, UUID> {

    List<AgentExecutionEntity> findByTaskIdOrderByCreatedAtAsc(UUID taskId);
}

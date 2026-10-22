package com.lucapodesta.agentoffice.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface TaskEventJpaRepository extends JpaRepository<TaskEventEntity, UUID> {

    List<TaskEventEntity> findByTaskIdOrderByCreatedAtAsc(UUID taskId);
}

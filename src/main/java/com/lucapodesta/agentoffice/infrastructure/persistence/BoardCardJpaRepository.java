package com.lucapodesta.agentoffice.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface BoardCardJpaRepository extends JpaRepository<BoardCardEntity, UUID> {
}

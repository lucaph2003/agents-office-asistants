package com.lucapodesta.agentoffice.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface BoardCardJpaRepository extends JpaRepository<BoardCardEntity, UUID> {

    List<BoardCardEntity> findByCardIdIn(Collection<String> cardIds);
}

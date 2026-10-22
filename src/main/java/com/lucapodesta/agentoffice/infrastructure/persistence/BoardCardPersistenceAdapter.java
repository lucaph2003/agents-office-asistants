package com.lucapodesta.agentoffice.infrastructure.persistence;

import com.lucapodesta.agentoffice.application.port.out.BoardCard;
import com.lucapodesta.agentoffice.application.port.out.BoardCardRepository;
import java.time.Clock;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
class BoardCardPersistenceAdapter implements BoardCardRepository {

    private final BoardCardJpaRepository jpa;
    private final Clock clock;

    BoardCardPersistenceAdapter(BoardCardJpaRepository jpa, Clock clock) {
        this.jpa = jpa;
        this.clock = clock;
    }

    @Override
    public void save(UUID taskId, BoardCard card) {
        BoardCardEntity entity = jpa.findById(taskId)
                .map(existing -> {
                    existing.apply(card, clock.instant());
                    return existing;
                })
                .orElseGet(() -> new BoardCardEntity(taskId, card, clock.instant()));
        jpa.save(entity);
    }

    @Override
    public Optional<BoardCard> findByTaskId(UUID taskId) {
        return jpa.findById(taskId).map(BoardCardEntity::toDomain);
    }

    @Override
    public Map<UUID, BoardCard> findByTaskIds(Collection<UUID> taskIds) {
        return jpa.findAllById(taskIds).stream()
                .collect(Collectors.toMap(BoardCardEntity::taskId, BoardCardEntity::toDomain));
    }
}

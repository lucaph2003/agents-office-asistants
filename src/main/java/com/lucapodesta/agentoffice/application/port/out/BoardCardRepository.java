package com.lucapodesta.agentoffice.application.port.out;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface BoardCardRepository {

    void save(UUID taskId, BoardCard card);

    Optional<BoardCard> findByTaskId(UUID taskId);

    Map<UUID, BoardCard> findByTaskIds(Collection<UUID> taskIds);
}

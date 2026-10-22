package com.lucapodesta.agentoffice.infrastructure.persistence;

import com.lucapodesta.agentoffice.application.port.out.BoardCard;
import com.lucapodesta.agentoffice.domain.task.TaskStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "task_board_cards")
class BoardCardEntity extends AssignedIdEntity {

    @Id
    private UUID taskId;

    private String cardId;
    private String cardUrl;

    @Enumerated(EnumType.STRING)
    private TaskStatus syncedStatus;

    @Column(updatable = false)
    private Instant createdAt;

    private Instant updatedAt;

    protected BoardCardEntity() {
    }

    BoardCardEntity(UUID taskId, BoardCard card, Instant now) {
        this.taskId = taskId;
        this.createdAt = now;
        apply(card, now);
    }

    void apply(BoardCard card, Instant now) {
        cardId = card.id();
        cardUrl = card.url();
        syncedStatus = card.syncedStatus();
        updatedAt = now;
    }

    BoardCard toDomain() {
        return new BoardCard(cardId, cardUrl, syncedStatus);
    }

    UUID taskId() {
        return taskId;
    }

    @Override
    public UUID getId() {
        return taskId;
    }
}

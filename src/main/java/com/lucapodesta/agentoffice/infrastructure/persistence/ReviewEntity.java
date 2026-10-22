package com.lucapodesta.agentoffice.infrastructure.persistence;

import com.lucapodesta.agentoffice.domain.review.Review;
import com.lucapodesta.agentoffice.domain.review.ReviewStatus;
import com.lucapodesta.agentoffice.domain.review.Reviewer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reviews")
class ReviewEntity extends AssignedIdEntity {

    @Id
    private UUID id;

    private UUID taskId;
    private UUID executionId;

    @Enumerated(EnumType.STRING)
    private Reviewer reviewer;

    @Enumerated(EnumType.STRING)
    private ReviewStatus status;

    @Column(columnDefinition = "text")
    private String feedback;

    private Instant createdAt;

    protected ReviewEntity() {
    }

    static ReviewEntity from(Review review) {
        ReviewEntity entity = new ReviewEntity();
        entity.id = review.id();
        entity.taskId = review.taskId();
        entity.executionId = review.executionId();
        entity.reviewer = review.reviewer();
        entity.status = review.status();
        entity.feedback = review.feedback();
        entity.createdAt = review.createdAt();
        return entity;
    }

    Review toDomain() {
        return new Review(id, taskId, executionId, reviewer, status, feedback, createdAt);
    }

    @Override
    public UUID getId() {
        return id;
    }
}

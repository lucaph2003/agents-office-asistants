package com.lucapodesta.agentoffice.infrastructure.web;

import com.lucapodesta.agentoffice.domain.review.Review;
import com.lucapodesta.agentoffice.domain.review.ReviewStatus;
import com.lucapodesta.agentoffice.domain.review.Reviewer;
import java.time.Instant;
import java.util.UUID;

public record ReviewResponse(UUID id, UUID executionId, Reviewer reviewer, ReviewStatus status, String feedback,
                             Instant createdAt) {

    static ReviewResponse from(Review review) {
        return new ReviewResponse(review.id(), review.executionId(), review.reviewer(), review.status(),
                review.feedback(), review.createdAt());
    }
}

package com.lucapodesta.agentoffice.domain.review;

import com.lucapodesta.agentoffice.domain.exception.DomainValidationException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Review(
        UUID id,
        UUID taskId,
        UUID executionId,
        Reviewer reviewer,
        ReviewStatus status,
        String feedback,
        Instant createdAt) {

    public static final int MAX_FEEDBACK_LENGTH = 4_000;

    public Review {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(executionId, "executionId");
        Objects.requireNonNull(reviewer, "reviewer");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        if (status != ReviewStatus.APPROVED && (feedback == null || feedback.isBlank())) {
            throw new DomainValidationException("Feedback is required when a review is not approved");
        }
        if (feedback != null && feedback.length() > MAX_FEEDBACK_LENGTH) {
            throw new DomainValidationException("Review feedback must be at most " + MAX_FEEDBACK_LENGTH + " characters");
        }
    }

    public static Review of(UUID taskId, UUID executionId, Reviewer reviewer, ReviewStatus status,
                            String feedback, Instant now) {
        return new Review(UUID.randomUUID(), taskId, executionId, reviewer, status, feedback, now);
    }
}

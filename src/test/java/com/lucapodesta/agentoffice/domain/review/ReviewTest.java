package com.lucapodesta.agentoffice.domain.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lucapodesta.agentoffice.domain.exception.DomainValidationException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ReviewTest {

    @Test
    void approvalNeedsNoFeedback() {
        Review review = review(ReviewStatus.APPROVED, null);

        assertThat(review.status()).isEqualTo(ReviewStatus.APPROVED);
    }

    @ParameterizedTest
    @EnumSource(value = ReviewStatus.class, names = {"CHANGES_REQUESTED", "REJECTED"})
    void nonApprovalRequiresFeedback(ReviewStatus status) {
        assertThatThrownBy(() -> review(status, " ")).isInstanceOf(DomainValidationException.class);
        assertThat(review(status, "Missing rollback plan").feedback()).isEqualTo("Missing rollback plan");
    }

    @Test
    void rejectsOversizedFeedback() {
        assertThatThrownBy(() -> review(ReviewStatus.REJECTED, "x".repeat(Review.MAX_FEEDBACK_LENGTH + 1)))
                .isInstanceOf(DomainValidationException.class);
    }

    private static Review review(ReviewStatus status, String feedback) {
        return Review.of(UUID.randomUUID(), UUID.randomUUID(), Reviewer.AGENT, status, feedback, Instant.now());
    }
}

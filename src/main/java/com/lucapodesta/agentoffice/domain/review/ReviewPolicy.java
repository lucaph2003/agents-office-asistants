package com.lucapodesta.agentoffice.domain.review;

import com.lucapodesta.agentoffice.domain.task.Task;

/**
 * Decides how a task proceeds after its output is reviewed.
 * The attempt limit is what guarantees the worker/reviewer loop always terminates.
 */
public class ReviewPolicy {

    private final int maxAttempts;

    public ReviewPolicy(int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        this.maxAttempts = maxAttempts;
    }

    public ReviewOutcome decide(Task task, ReviewStatus verdict) {
        return switch (verdict) {
            case APPROVED -> task.requiresHumanApproval()
                    ? ReviewOutcome.AWAIT_HUMAN_APPROVAL
                    : ReviewOutcome.COMPLETE;
            case CHANGES_REQUESTED -> task.attempt() < maxAttempts
                    ? ReviewOutcome.RETRY
                    : ReviewOutcome.FAIL_MAX_RETRIES;
            case REJECTED -> ReviewOutcome.FAIL_REJECTED;
        };
    }

    public int maxAttempts() {
        return maxAttempts;
    }
}

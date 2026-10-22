package com.lucapodesta.agentoffice.domain.review;

/** What the task should do next after a review. */
public enum ReviewOutcome {
    COMPLETE,
    AWAIT_HUMAN_APPROVAL,
    RETRY,
    FAIL_MAX_RETRIES,
    FAIL_REJECTED
}

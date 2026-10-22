package com.lucapodesta.agentoffice.domain.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lucapodesta.agentoffice.domain.agent.AgentType;
import com.lucapodesta.agentoffice.domain.agent.ManagerDecision;
import com.lucapodesta.agentoffice.domain.task.Task;
import com.lucapodesta.agentoffice.domain.task.TaskPriority;
import com.lucapodesta.agentoffice.domain.task.TaskSource;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ReviewPolicyTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private final ReviewPolicy policy = new ReviewPolicy(3);

    @Test
    void approvalCompletesTask() {
        assertThat(policy.decide(taskInReview(false), ReviewStatus.APPROVED)).isEqualTo(ReviewOutcome.COMPLETE);
    }

    @Test
    void approvalWaitsForHumanWhenRequired() {
        assertThat(policy.decide(taskInReview(true), ReviewStatus.APPROVED))
                .isEqualTo(ReviewOutcome.AWAIT_HUMAN_APPROVAL);
    }

    @Test
    void rejectionFailsTaskImmediately() {
        assertThat(policy.decide(taskInReview(false), ReviewStatus.REJECTED)).isEqualTo(ReviewOutcome.FAIL_REJECTED);
    }

    @Test
    void changesRequestedRetriesUntilTheLimit() {
        Task task = taskInReview(false);

        assertThat(policy.decide(task, ReviewStatus.CHANGES_REQUESTED)).isEqualTo(ReviewOutcome.RETRY);
        task.requestChanges(NOW);
        task.submitForReview(NOW);
        assertThat(policy.decide(task, ReviewStatus.CHANGES_REQUESTED)).isEqualTo(ReviewOutcome.RETRY);
        task.requestChanges(NOW);
        task.submitForReview(NOW);

        assertThat(task.attempt()).isEqualTo(3);
        assertThat(policy.decide(task, ReviewStatus.CHANGES_REQUESTED)).isEqualTo(ReviewOutcome.FAIL_MAX_RETRIES);
    }

    @Test
    void singleAttemptPolicyNeverRetries() {
        assertThat(new ReviewPolicy(1).decide(taskInReview(false), ReviewStatus.CHANGES_REQUESTED))
                .isEqualTo(ReviewOutcome.FAIL_MAX_RETRIES);
    }

    @Test
    void requiresAtLeastOneAttempt() {
        assertThatThrownBy(() -> new ReviewPolicy(0)).isInstanceOf(IllegalArgumentException.class);
    }

    private static Task taskInReview(boolean requiresHumanApproval) {
        Task task = Task.create("Title", "Description", TaskPriority.MEDIUM, TaskSource.API, null, requiresHumanApproval, NOW);
        task.assign(new ManagerDecision(AgentType.DEVELOPER, TaskPriority.MEDIUM, "technical"), NOW);
        task.start(NOW);
        task.submitForReview(NOW);
        return task;
    }
}

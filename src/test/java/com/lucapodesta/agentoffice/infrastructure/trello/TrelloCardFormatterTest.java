package com.lucapodesta.agentoffice.infrastructure.trello;

import static org.assertj.core.api.Assertions.assertThat;

import com.lucapodesta.agentoffice.application.port.out.TaskCard;
import com.lucapodesta.agentoffice.domain.agent.AgentType;
import com.lucapodesta.agentoffice.domain.agent.ManagerDecision;
import com.lucapodesta.agentoffice.domain.execution.AgentExecution;
import com.lucapodesta.agentoffice.domain.review.Review;
import com.lucapodesta.agentoffice.domain.review.ReviewStatus;
import com.lucapodesta.agentoffice.domain.review.Reviewer;
import com.lucapodesta.agentoffice.domain.task.Task;
import com.lucapodesta.agentoffice.domain.task.TaskPriority;
import com.lucapodesta.agentoffice.domain.task.TaskSource;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TrelloCardFormatterTest {

    private static final Instant T0 = Instant.parse("2026-03-01T09:30:00Z");
    private static final Instant T1 = Instant.parse("2026-03-01T09:45:00Z");

    @Test
    void newTaskShowsIdentityAndDescription() {
        Task task = Task.create("Plan", "Migrate to Java 21", null, TaskSource.TELEGRAM, "chat", false, T0);

        String description = TrelloCardFormatter.description(new TaskCard(task, null, null));

        assertThat(description)
                .contains("**Task ID:** `" + task.id() + "`")
                .contains("**Status:** INBOX")
                .contains("**Assigned agent:** —")
                .contains("### Description\nMigrate to Java 21")
                .contains("Created 2026-03-01 09:30 UTC")
                .doesNotContain("Latest execution")
                .doesNotContain("### Review");
    }

    @Test
    void completedTaskShowsLatestWorkReviewAndCompletion() {
        Task task = Task.create("Plan", "x".repeat(3_000), null, TaskSource.API, null, false, T0);
        task.assign(new ManagerDecision(AgentType.DEVELOPER, TaskPriority.HIGH, "technical"), T0);
        task.start(T0);
        task.submitForReview(T0);
        task.complete(T1);
        AgentExecution work = AgentExecution.create(task.id(), AgentType.DEVELOPER, 1, "qwen3", "developer-v1", T0);
        work.start(T0);
        work.succeed("# Full plan with many details", "A three-phase plan", T1);
        Review review = Review.of(task.id(), work.id(), Reviewer.AGENT, ReviewStatus.APPROVED, null, T1);

        String description = TrelloCardFormatter.description(new TaskCard(task, work, review));

        assertThat(description)
                .contains("**Assigned agent:** DEVELOPER")
                .contains("DEVELOPER · attempt 1 · SUCCESS\nA three-phase plan")
                .contains("### Review\nAPPROVED")
                .contains("Completed 2026-03-01 09:45 UTC")
                .doesNotContain("# Full plan with many details")
                .contains("x".repeat(1_500) + "…")
                .doesNotContain("x".repeat(1_501));
        assertThat(TrelloCardFormatter.name(task)).isEqualTo("Plan");
    }
}

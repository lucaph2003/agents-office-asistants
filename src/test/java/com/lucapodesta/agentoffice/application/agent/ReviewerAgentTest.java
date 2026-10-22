package com.lucapodesta.agentoffice.application.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lucapodesta.agentoffice.application.support.ScriptedLanguageModel;
import com.lucapodesta.agentoffice.domain.agent.AgentType;
import com.lucapodesta.agentoffice.domain.exception.InvalidAgentOutputException;
import com.lucapodesta.agentoffice.domain.review.Review;
import com.lucapodesta.agentoffice.domain.review.ReviewStatus;
import com.lucapodesta.agentoffice.domain.task.Task;
import com.lucapodesta.agentoffice.domain.task.TaskSource;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ReviewerAgentTest {

    private final ScriptedLanguageModel model = new ScriptedLanguageModel();
    private final ReviewerAgent reviewer = new ReviewerAgent(model, ScriptedLanguageModel.PROMPTS);
    private final Task task = Task.create("Title", "Description", null, TaskSource.API, null, false, Instant.now());

    @Test
    void readsVerdictAndShowsOutputAsData() {
        model.answer(AgentType.REVIEWER, "{\"verdict\": \"changes requested\", \"feedback\": \"Missing tests\"}");

        AgentReply<ReviewVerdict> reply = reviewer.review(task, "the worker output");

        assertThat(reply.value()).isEqualTo(new ReviewVerdict(ReviewStatus.CHANGES_REQUESTED, "Missing tests"));
        assertThat(reply.summary()).isEqualTo("CHANGES_REQUESTED: Missing tests");
        assertThat(model.requestsFor(AgentType.REVIEWER).getFirst().userPrompt())
                .contains("<output>\nthe worker output\n</output>");
    }

    @Test
    void approvalMayOmitFeedback() {
        model.answer(AgentType.REVIEWER, "{\"verdict\": \"APPROVED\"}");

        assertThat(reviewer.review(task, "out").value()).isEqualTo(new ReviewVerdict(ReviewStatus.APPROVED, null));
    }

    @Test
    void criticismWithoutFeedbackIsUnusable() {
        model.answer(AgentType.REVIEWER, "{\"verdict\": \"REJECTED\"}", "{\"feedback\": \"meh\"}");

        assertThatThrownBy(() -> reviewer.review(task, "out")).isInstanceOf(InvalidAgentOutputException.class);
        assertThatThrownBy(() -> reviewer.review(task, "out")).isInstanceOf(InvalidAgentOutputException.class);
    }

    @Test
    void capsVeryLongFeedback() {
        model.answer(AgentType.REVIEWER,
                "{\"verdict\": \"REJECTED\", \"feedback\": \"%s\"}".formatted("x".repeat(Review.MAX_FEEDBACK_LENGTH + 50)));

        assertThat(reviewer.review(task, "out").value().feedback()).hasSize(Review.MAX_FEEDBACK_LENGTH);
    }
}

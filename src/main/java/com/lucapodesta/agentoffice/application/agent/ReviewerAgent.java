package com.lucapodesta.agentoffice.application.agent;

import com.lucapodesta.agentoffice.application.port.out.LanguageModelPort;
import com.lucapodesta.agentoffice.application.port.out.PromptCatalog;
import com.lucapodesta.agentoffice.domain.agent.AgentType;
import com.lucapodesta.agentoffice.domain.exception.InvalidAgentOutputException;
import com.lucapodesta.agentoffice.domain.review.Review;
import com.lucapodesta.agentoffice.domain.review.ReviewStatus;
import com.lucapodesta.agentoffice.domain.task.Task;

/** Judges a worker's output against the task. Its verdict feeds the review policy. */
public class ReviewerAgent extends LlmAgent {

    public ReviewerAgent(LanguageModelPort languageModel, PromptCatalog prompts) {
        super(AgentType.REVIEWER, languageModel, prompts);
    }

    public AgentReply<ReviewVerdict> review(Task task, String output) {
        JsonReply reply = askJson(describe(task) + """

                <output>
                %s
                </output>
                """.formatted(output));
        ReviewStatus status = reply.enumValue("verdict", ReviewStatus.class)
                .orElseThrow(() -> new InvalidAgentOutputException("Missing field 'verdict'"));
        String feedback = reply.text("feedback").map(ReviewerAgent::limit).orElse(null);
        if (status != ReviewStatus.APPROVED && feedback == null) {
            throw new InvalidAgentOutputException("Verdict " + status + " requires feedback");
        }
        String summary = feedback == null ? status.name() : status + ": " + feedback;
        return new AgentReply<>(new ReviewVerdict(status, feedback), reply.raw(), summary);
    }

    private static String limit(String feedback) {
        return feedback.length() <= Review.MAX_FEEDBACK_LENGTH
                ? feedback
                : feedback.substring(0, Review.MAX_FEEDBACK_LENGTH);
    }
}

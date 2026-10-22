package com.lucapodesta.agentoffice.application.port.out;

import com.lucapodesta.agentoffice.domain.execution.AgentExecution;
import com.lucapodesta.agentoffice.domain.review.Review;
import com.lucapodesta.agentoffice.domain.task.Task;

/**
 * Everything a board needs to render a task.
 *
 * @param latestWork   the most recent worker execution, or null
 * @param latestReview the most recent review, or null
 */
public record TaskCard(Task task, AgentExecution latestWork, Review latestReview) {
}

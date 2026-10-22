package com.lucapodesta.agentoffice.application.port.out;

import com.lucapodesta.agentoffice.domain.agent.AgentType;
import com.lucapodesta.agentoffice.domain.review.ReviewStatus;
import com.lucapodesta.agentoffice.domain.task.TaskEventType;
import com.lucapodesta.agentoffice.domain.task.TaskPriority;
import com.lucapodesta.agentoffice.domain.task.TaskSource;
import com.lucapodesta.agentoffice.domain.task.TaskStatus;
import java.util.UUID;

/**
 * @param summary           short result of the latest worker execution, if any
 * @param reason            why the task ended up here (failure or block reason), if any
 * @param externalReference where the task came from, e.g. the Telegram chat id
 */
public record TaskNotification(
        TaskEventType event,
        UUID taskId,
        String title,
        TaskStatus status,
        TaskPriority priority,
        AgentType agent,
        int attempts,
        ReviewStatus review,
        String summary,
        String reason,
        String boardUrl,
        TaskSource source,
        String externalReference) {
}

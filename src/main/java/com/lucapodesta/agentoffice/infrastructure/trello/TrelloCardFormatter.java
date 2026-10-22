package com.lucapodesta.agentoffice.infrastructure.trello;

import com.lucapodesta.agentoffice.application.port.out.TaskCard;
import com.lucapodesta.agentoffice.domain.execution.AgentExecution;
import com.lucapodesta.agentoffice.domain.task.Task;
import com.lucapodesta.agentoffice.domain.task.TaskStatus;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** Renders a task as a Trello card. Full agent output stays in the API, not on the card. */
final class TrelloCardFormatter {

    private static final int MAX_DESCRIPTION = 1_500;
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private TrelloCardFormatter() {
    }

    static String name(Task task) {
        return switch (task.status()) {
            case FAILED -> "[FAILED] " + task.title();
            case CANCELLED -> "[CANCELLED] " + task.title();
            default -> task.title();
        };
    }

    static String description(TaskCard card) {
        Task task = card.task();
        StringBuilder text = new StringBuilder()
                .append("**Task ID:** `").append(task.id()).append("`\n")
                .append("**Status:** ").append(task.status()).append('\n')
                .append("**Priority:** ").append(task.priority()).append('\n')
                .append("**Assigned agent:** ").append(task.assignedAgent() == null ? "—" : task.assignedAgent()).append('\n')
                .append("**Source:** ").append(task.source()).append("\n\n")
                .append("---\n### Description\n")
                .append(limit(task.description(), MAX_DESCRIPTION)).append("\n\n");
        AgentExecution work = card.latestWork();
        if (work != null) {
            text.append("---\n### Latest execution\n")
                    .append(work.agentType()).append(" · attempt ").append(work.attempt())
                    .append(" · ").append(work.status()).append('\n');
            if (work.summary() != null) {
                text.append(work.summary()).append('\n');
            }
            text.append('\n');
        }
        if (card.latestReview() != null) {
            text.append("### Review\n").append(card.latestReview().status()).append("\n\n");
        }
        text.append("---\nCreated ").append(TIMESTAMP.format(task.createdAt()))
                .append(" · Updated ").append(TIMESTAMP.format(task.updatedAt()));
        if (task.status() == TaskStatus.DONE && task.completedAt() != null) {
            text.append(" · Completed ").append(TIMESTAMP.format(task.completedAt()));
        }
        return text.toString();
    }

    private static String limit(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}

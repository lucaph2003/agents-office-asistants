package com.lucapodesta.agentoffice.application.port.out;

import com.lucapodesta.agentoffice.domain.task.TaskStatus;

/**
 * @param syncedStatus the task status the card currently reflects
 */
public record BoardCard(String id, String url, TaskStatus syncedStatus) {

    public BoardCard withStatus(TaskStatus status) {
        return new BoardCard(id, url, status);
    }
}

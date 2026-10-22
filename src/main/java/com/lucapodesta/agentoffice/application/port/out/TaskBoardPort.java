package com.lucapodesta.agentoffice.application.port.out;

/**
 * Visual board where each task is a card. The board mirrors the task; it never drives it.
 *
 * @throws com.lucapodesta.agentoffice.domain.exception.TaskBoardException when the board cannot be reached
 */
public interface TaskBoardPort {

    BoardCard createCard(TaskCard card);

    /** Moves the card to the column of the task's status and refreshes its content. Idempotent. */
    void updateCard(String cardId, TaskCard card);

    void addComment(String cardId, String text);
}

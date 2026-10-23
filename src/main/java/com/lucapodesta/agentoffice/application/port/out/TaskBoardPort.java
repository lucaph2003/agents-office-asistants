package com.lucapodesta.agentoffice.application.port.out;

import java.util.List;

/**
 * Visual board where each task is a card. The board mirrors the task's state, and its inbox
 * column doubles as a durable place to drop new tasks while the system is offline.
 *
 * @throws com.lucapodesta.agentoffice.domain.exception.TaskBoardException when the board cannot be reached
 */
public interface TaskBoardPort {

    BoardCard createCard(TaskCard card);

    /** Moves the card to the column of the task's status and refreshes its content. Idempotent. */
    void updateCard(String cardId, TaskCard card);

    void addComment(String cardId, String text);

    /** Cards currently in the inbox column, whether or not they already belong to a task. */
    List<InboxCard> inboxCards();
}

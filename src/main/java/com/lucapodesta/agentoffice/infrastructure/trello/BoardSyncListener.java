package com.lucapodesta.agentoffice.infrastructure.trello;

import com.lucapodesta.agentoffice.application.dto.TaskEventRecorded;
import com.lucapodesta.agentoffice.application.service.TaskBoardSynchronizer;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Syncs the board right after each commit, on the thread that made the change, so updates for
 * a task reach the board in order. It runs before the pipeline is dispatched, which guarantees
 * the card exists before any agent starts working.
 */
class BoardSyncListener {

    private final TaskBoardSynchronizer synchronizer;

    BoardSyncListener(TaskBoardSynchronizer synchronizer) {
        this.synchronizer = synchronizer;
    }

    @Order(Ordered.HIGHEST_PRECEDENCE)
    @TransactionalEventListener(fallbackExecution = true)
    void on(TaskEventRecorded recorded) {
        synchronizer.onEvent(recorded.event());
    }
}

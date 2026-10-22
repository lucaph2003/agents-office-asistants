package com.lucapodesta.agentoffice.infrastructure.notification;

import com.lucapodesta.agentoffice.application.dto.TaskEventRecorded;
import com.lucapodesta.agentoffice.application.service.TaskNotifier;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.event.TransactionalEventListener;

/** Runs after the board sync, so notifications can link to an up-to-date card. */
class NotificationListener {

    private final TaskNotifier notifier;

    NotificationListener(TaskNotifier notifier) {
        this.notifier = notifier;
    }

    @Order(Ordered.HIGHEST_PRECEDENCE + 10)
    @TransactionalEventListener(fallbackExecution = true)
    void on(TaskEventRecorded recorded) {
        notifier.onEvent(recorded.event());
    }
}

package com.lucapodesta.agentoffice.infrastructure.observability;

import com.lucapodesta.agentoffice.application.dto.TaskEventRecorded;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Counts committed audit events by type: tasks created, completed, failed or blocked, executions
 * that failed, board and notification problems. Derived from the audit trail, so the domain
 * stays free of metrics code.
 */
@Component
class TaskEventMetrics {

    private final MeterRegistry meters;

    TaskEventMetrics(MeterRegistry meters) {
        this.meters = meters;
    }

    @TransactionalEventListener(fallbackExecution = true)
    void on(TaskEventRecorded recorded) {
        Counter.builder("agent_office.task.events")
                .description("Committed task audit events")
                .tag("type", recorded.event().type().name())
                .register(meters)
                .increment();
    }
}

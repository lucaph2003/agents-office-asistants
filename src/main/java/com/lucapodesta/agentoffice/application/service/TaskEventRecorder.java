package com.lucapodesta.agentoffice.application.service;

import com.lucapodesta.agentoffice.application.dto.TaskEventRecorded;
import com.lucapodesta.agentoffice.application.port.out.TaskEventRepository;
import com.lucapodesta.agentoffice.domain.task.TaskEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

/** Appends to the audit trail and announces the event to whoever listens (board, notifications). */
public class TaskEventRecorder {

    private static final Logger log = LoggerFactory.getLogger(TaskEventRecorder.class);

    private final TaskEventRepository events;
    private final ApplicationEventPublisher publisher;

    public TaskEventRecorder(TaskEventRepository events, ApplicationEventPublisher publisher) {
        this.events = events;
        this.publisher = publisher;
    }

    public void record(TaskEvent event) {
        events.append(event);
        log.info("event={} taskId={} actor={} details=\"{}\"", event.type(), event.taskId(), event.actor(), event.details());
        publisher.publishEvent(new TaskEventRecorded(event));
    }
}

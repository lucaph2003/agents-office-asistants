package com.lucapodesta.agentoffice.application.dto;

import com.lucapodesta.agentoffice.domain.task.TaskEvent;

/** Published for every audit event, so integrations can react once the change is committed. */
public record TaskEventRecorded(TaskEvent event) {
}

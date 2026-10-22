package com.lucapodesta.agentoffice.application.dto;

import com.lucapodesta.agentoffice.domain.agent.AgentType;
import com.lucapodesta.agentoffice.domain.task.TaskPriority;
import com.lucapodesta.agentoffice.domain.task.TaskSource;
import com.lucapodesta.agentoffice.domain.task.TaskStatus;

/** Optional criteria; null fields are ignored. */
public record TaskFilter(TaskStatus status, AgentType agent, TaskPriority priority, TaskSource source,
                         String externalReference) {
}

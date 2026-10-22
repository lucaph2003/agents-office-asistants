package com.lucapodesta.agentoffice.infrastructure.execution;

import com.lucapodesta.agentoffice.application.dto.TaskExecutionRequestedEvent;
import com.lucapodesta.agentoffice.application.dto.TaskSubmittedEvent;
import com.lucapodesta.agentoffice.application.service.TaskPipeline;
import com.lucapodesta.agentoffice.infrastructure.config.PipelineProperties;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Runs the pipeline in the background once the request that created or resumed the task has
 * committed, so callers (n8n, the API) get an immediate answer while the models work.
 */
@Component
class TaskPipelineTrigger {

    private static final Logger log = LoggerFactory.getLogger(TaskPipelineTrigger.class);

    private final TaskPipeline pipeline;
    private final PipelineProperties properties;

    TaskPipelineTrigger(TaskPipeline pipeline, PipelineProperties properties) {
        this.pipeline = pipeline;
        this.properties = properties;
    }

    @Async
    @TransactionalEventListener
    void onSubmitted(TaskSubmittedEvent event) {
        if (properties.autoStart()) {
            run(event.taskId());
        }
    }

    @Async
    @TransactionalEventListener
    void onExecutionRequested(TaskExecutionRequestedEvent event) {
        run(event.taskId());
    }

    private void run(UUID taskId) {
        try {
            pipeline.process(taskId);
        } catch (RuntimeException ex) {
            log.error("event=PIPELINE_CRASHED taskId={}", taskId, ex);
        }
    }
}

package com.lucapodesta.agentoffice.domain.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lucapodesta.agentoffice.domain.agent.AgentType;
import com.lucapodesta.agentoffice.domain.exception.DomainValidationException;
import com.lucapodesta.agentoffice.domain.exception.ErrorCode;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgentExecutionTest {

    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant T1 = T0.plusSeconds(30);

    @Test
    void successfulRunRecordsOutputAndTiming() {
        AgentExecution execution = newExecution();
        assertThat(execution.status()).isEqualTo(ExecutionStatus.PENDING);

        execution.start(T0);
        execution.succeed("plan", T1);

        assertThat(execution.status()).isEqualTo(ExecutionStatus.SUCCESS);
        assertThat(execution.output()).isEqualTo("plan");
        assertThat(execution.startedAt()).isEqualTo(T0);
        assertThat(execution.finishedAt()).isEqualTo(T1);
        assertThat(execution.errorCode()).isNull();
    }

    @Test
    void failedRunRecordsErrorCode() {
        AgentExecution execution = newExecution();
        execution.start(T0);

        execution.fail(ErrorCode.OLLAMA_UNAVAILABLE, T1);

        assertThat(execution.status()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(execution.errorCode()).isEqualTo(ErrorCode.OLLAMA_UNAVAILABLE);
        assertThat(execution.output()).isNull();
    }

    @Test
    void onlySuccessfulRunsCanBeRejected() {
        AgentExecution succeeded = newExecution();
        succeeded.start(T0);
        succeeded.succeed("plan", T1);
        succeeded.reject();

        AgentExecution running = newExecution();
        running.start(T0);

        assertThat(succeeded.status()).isEqualTo(ExecutionStatus.REJECTED);
        assertThatThrownBy(running::reject).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cannotFinishWithoutStarting() {
        AgentExecution execution = newExecution();

        assertThatThrownBy(() -> execution.succeed("plan", T1)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> execution.fail(ErrorCode.INTERNAL_ERROR, T1)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void truncatesOversizedOutput() {
        AgentExecution execution = newExecution();
        execution.start(T0);

        execution.succeed("a".repeat(AgentExecution.MAX_OUTPUT_LENGTH * 2), T1);

        assertThat(execution.output())
                .hasSize(AgentExecution.MAX_OUTPUT_LENGTH)
                .endsWith(AgentExecution.TRUNCATION_MARKER);
    }

    @Test
    void attemptsStartAtOne() {
        assertThatThrownBy(() -> AgentExecution.create(UUID.randomUUID(), AgentType.DEVELOPER, 0, "qwen3", "v1", T0))
                .isInstanceOf(DomainValidationException.class);
    }

    private static AgentExecution newExecution() {
        return AgentExecution.create(UUID.randomUUID(), AgentType.DEVELOPER, 1, "qwen3", "v1", T0);
    }
}

package com.lucapodesta.agentoffice.domain.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lucapodesta.agentoffice.domain.exception.DomainValidationException;
import com.lucapodesta.agentoffice.domain.task.TaskPriority;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ManagerDecisionTest {

    @ParameterizedTest
    @EnumSource(value = AgentType.class, names = {"RESEARCH", "DEVELOPER"})
    void acceptsWorkerAgents(AgentType worker) {
        ManagerDecision decision = new ManagerDecision(worker, TaskPriority.LOW, "fits");

        assertThat(decision.agent()).isEqualTo(worker);
    }

    @ParameterizedTest
    @EnumSource(value = AgentType.class, names = {"MANAGER", "REVIEWER"})
    void cannotRouteToCoordinationAgents(AgentType agent) {
        assertThatThrownBy(() -> new ManagerDecision(agent, TaskPriority.LOW, "loop"))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("worker");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void requiresAReason(String reason) {
        assertThatThrownBy(() -> new ManagerDecision(AgentType.DEVELOPER, TaskPriority.LOW, reason))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void requiresAPriority() {
        assertThatThrownBy(() -> new ManagerDecision(AgentType.DEVELOPER, null, "reason"))
                .isInstanceOf(DomainValidationException.class);
    }
}

package com.lucapodesta.agentoffice.application.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lucapodesta.agentoffice.application.support.ScriptedLanguageModel;
import com.lucapodesta.agentoffice.domain.agent.AgentType;
import com.lucapodesta.agentoffice.domain.exception.InvalidAgentOutputException;
import com.lucapodesta.agentoffice.domain.task.Task;
import com.lucapodesta.agentoffice.domain.task.TaskSource;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class WorkerAgentTest {

    private final ScriptedLanguageModel model = new ScriptedLanguageModel();
    private final WorkerAgent developer = new WorkerAgent(AgentType.DEVELOPER, model, ScriptedLanguageModel.PROMPTS);
    private final Task task = Task.create("Title", "Description", null, TaskSource.API, null, false, Instant.now());

    @Test
    void storesContentAsOutputAndSummaryForHumans() {
        model.answer(AgentType.DEVELOPER, "{\"summary\": \"A plan\", \"content\": \"# Plan\\nDetails\"}");

        AgentReply<WorkProduct> reply = developer.work(task, null);

        assertThat(reply.output()).isEqualTo("# Plan\nDetails");
        assertThat(reply.summary()).isEqualTo("A plan");
    }

    @Test
    void derivesSummaryFromContentWhenMissing() {
        model.answer(AgentType.DEVELOPER, "{\"content\": \"\\n## Migration plan\\nStep one\"}");

        assertThat(developer.work(task, null).summary()).isEqualTo("Migration plan");
    }

    @Test
    void contentIsMandatory() {
        model.answer(AgentType.DEVELOPER, "{\"summary\": \"Nothing else\"}");

        assertThatThrownBy(() -> developer.work(task, null))
                .isInstanceOf(InvalidAgentOutputException.class)
                .hasMessageContaining("content");
    }

    @Test
    void revisionIncludesPreviousOutputAndFeedback() {
        model.answer(AgentType.DEVELOPER, "{\"content\": \"v2\"}");

        developer.work(task, new Revision("v1", "Add tests"));

        assertThat(model.requestsFor(AgentType.DEVELOPER).getFirst().userPrompt())
                .contains("<previous_output>\nv1\n</previous_output>")
                .contains("<review_feedback>\nAdd tests\n</review_feedback>");
    }

    @Test
    void onlyWorkersCanBeWorkers() {
        assertThatThrownBy(() -> new WorkerAgent(AgentType.REVIEWER, model, ScriptedLanguageModel.PROMPTS))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

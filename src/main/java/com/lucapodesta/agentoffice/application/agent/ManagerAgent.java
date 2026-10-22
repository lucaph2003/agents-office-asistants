package com.lucapodesta.agentoffice.application.agent;

import com.lucapodesta.agentoffice.application.port.out.LanguageModelPort;
import com.lucapodesta.agentoffice.application.port.out.PromptCatalog;
import com.lucapodesta.agentoffice.domain.agent.AgentType;
import com.lucapodesta.agentoffice.domain.agent.ManagerDecision;
import com.lucapodesta.agentoffice.domain.exception.DomainValidationException;
import com.lucapodesta.agentoffice.domain.exception.InvalidAgentOutputException;
import com.lucapodesta.agentoffice.domain.task.Task;
import com.lucapodesta.agentoffice.domain.task.TaskPriority;

/** Routes a task to a worker and sets its priority. Never does the work itself. */
public class ManagerAgent extends LlmAgent {

    public ManagerAgent(LanguageModelPort languageModel, PromptCatalog prompts) {
        super(AgentType.MANAGER, languageModel, prompts);
    }

    public AgentReply<ManagerDecision> route(Task task) {
        JsonReply reply = askJson(describe(task));
        AgentType agent = reply.enumValue("agent", AgentType.class)
                .orElseThrow(() -> new InvalidAgentOutputException("Missing field 'agent'"));
        TaskPriority priority = reply.enumValue("priority", TaskPriority.class).orElse(task.priority());
        String reason = reply.text("reason").orElse("No reason given");
        try {
            ManagerDecision decision = new ManagerDecision(agent, priority, reason);
            return new AgentReply<>(decision, reply.raw(),
                    "Assigned to %s (%s): %s".formatted(agent, priority, reason));
        } catch (DomainValidationException ex) {
            throw new InvalidAgentOutputException(ex.getMessage());
        }
    }
}

package com.lucapodesta.agentoffice.application.agent;

import com.lucapodesta.agentoffice.application.port.out.LanguageModelPort;
import com.lucapodesta.agentoffice.application.port.out.LlmRequest;
import com.lucapodesta.agentoffice.application.port.out.PromptCatalog;
import com.lucapodesta.agentoffice.domain.agent.AgentType;
import com.lucapodesta.agentoffice.domain.task.Task;

/** Shared plumbing for agents backed by a language model. Agents never see the provider. */
public abstract class LlmAgent {

    private final AgentType type;
    private final LanguageModelPort languageModel;
    private final PromptCatalog.Prompt systemPrompt;

    protected LlmAgent(AgentType type, LanguageModelPort languageModel, PromptCatalog prompts) {
        this.type = type;
        this.languageModel = languageModel;
        this.systemPrompt = prompts.systemPrompt(type);
    }

    public AgentType type() {
        return type;
    }

    public String model() {
        return languageModel.modelFor(type);
    }

    public String promptVersion() {
        return systemPrompt.version();
    }

    protected JsonReply askJson(String userPrompt) {
        String answer = languageModel.generate(new LlmRequest(type, systemPrompt.text(), userPrompt, true)).content();
        return JsonReply.parse(answer);
    }

    /** Task content is wrapped in tags so prompts can tell the model to treat it as data. */
    protected static String describe(Task task) {
        return """
                <task>
                Title: %s
                Priority: %s
                Description:
                %s
                </task>
                """.formatted(task.title(), task.priority(), task.description());
    }
}

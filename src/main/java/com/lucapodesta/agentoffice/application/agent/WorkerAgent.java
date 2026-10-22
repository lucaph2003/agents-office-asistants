package com.lucapodesta.agentoffice.application.agent;

import com.lucapodesta.agentoffice.application.port.out.LanguageModelPort;
import com.lucapodesta.agentoffice.application.port.out.PromptCatalog;
import com.lucapodesta.agentoffice.domain.agent.AgentType;
import com.lucapodesta.agentoffice.domain.task.Task;

/**
 * Research and Developer agents share this contract and differ only in their system prompt.
 */
public class WorkerAgent extends LlmAgent {

    public WorkerAgent(AgentType type, LanguageModelPort languageModel, PromptCatalog prompts) {
        super(type, languageModel, prompts);
        if (!type.isWorker()) {
            throw new IllegalArgumentException(type + " is not a worker agent");
        }
    }

    /**
     * @param revision previous attempt and reviewer feedback, or null on the first attempt
     */
    public AgentReply<WorkProduct> work(Task task, Revision revision) {
        JsonReply reply = askJson(prompt(task, revision));
        String content = reply.requiredText("content");
        String summary = reply.text("summary").orElseGet(() -> firstLine(content));
        return new AgentReply<>(new WorkProduct(summary, content), content, summary);
    }

    private static String prompt(Task task, Revision revision) {
        if (revision == null) {
            return describe(task);
        }
        return describe(task) + """

                Your previous attempt was reviewed and needs changes.
                <previous_output>
                %s
                </previous_output>
                <review_feedback>
                %s
                </review_feedback>
                Produce a complete new version that addresses every point of the feedback.
                """.formatted(revision.previousOutput(), revision.feedback());
    }

    private static String firstLine(String content) {
        String line = content.lines().filter(l -> !l.isBlank()).findFirst().orElse(content);
        return line.replaceFirst("^#+\\s*", "");
    }
}

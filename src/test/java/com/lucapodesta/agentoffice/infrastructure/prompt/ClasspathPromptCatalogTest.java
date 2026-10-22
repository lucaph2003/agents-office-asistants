package com.lucapodesta.agentoffice.infrastructure.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lucapodesta.agentoffice.application.port.out.PromptCatalog.Prompt;
import com.lucapodesta.agentoffice.domain.agent.AgentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ClasspathPromptCatalogTest {

    private final ClasspathPromptCatalog catalog = new ClasspathPromptCatalog();

    @ParameterizedTest
    @EnumSource(AgentType.class)
    void everyAgentHasAVersionedPromptAskingForJson(AgentType agent) {
        Prompt prompt = catalog.systemPrompt(agent);

        assertThat(prompt.version()).isEqualTo(agent.name().toLowerCase() + "-v1");
        assertThat(prompt.text()).doesNotStartWith("---").contains("single JSON object");
    }

    @Test
    void parsesFrontMatter() {
        Prompt prompt = ClasspathPromptCatalog.parse("x.md", "---\nversion: manager-v2\nowner: luca\n---\n\nBody text\n");

        assertThat(prompt).isEqualTo(new Prompt("manager-v2", "Body text"));
    }

    @Test
    void rejectsMalformedPrompts() {
        assertThatThrownBy(() -> ClasspathPromptCatalog.parse("a.md", "No front matter"))
                .hasMessageContaining("front matter");
        assertThatThrownBy(() -> ClasspathPromptCatalog.parse("b.md", "---\nowner: luca\n---\nBody"))
                .hasMessageContaining("version");
        assertThatThrownBy(() -> ClasspathPromptCatalog.parse("c.md", "---\nversion: v1\n---\n   "))
                .hasMessageContaining("empty");
    }

    @Test
    void failsFastWhenAPromptIsMissing() {
        assertThatThrownBy(() -> new ClasspathPromptCatalog("no-such-dir"))
                .hasMessageContaining("Missing prompt no-such-dir/manager-system.md");
    }
}

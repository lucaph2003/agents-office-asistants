package com.lucapodesta.agentoffice.infrastructure.prompt;

import com.lucapodesta.agentoffice.application.port.out.PromptCatalog;
import com.lucapodesta.agentoffice.domain.agent.AgentType;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Loads one system prompt per agent from {@code prompts/<agent>-system.md}. Each file starts with
 * a front matter block declaring its {@code version}, which is stored with every execution.
 * All prompts are read at startup so a missing or malformed file fails fast.
 */
@Component
public class ClasspathPromptCatalog implements PromptCatalog {

    private static final Pattern FRONT_MATTER =
            Pattern.compile("\\A---\\R(?<header>.*?)\\R---\\R(?<body>.*)\\z", Pattern.DOTALL);
    private static final Pattern VERSION = Pattern.compile("^version:\\s*(\\S+)\\s*$", Pattern.MULTILINE);

    private final Map<AgentType, Prompt> prompts = new EnumMap<>(AgentType.class);

    public ClasspathPromptCatalog() {
        this("prompts");
    }

    ClasspathPromptCatalog(String directory) {
        for (AgentType agent : AgentType.values()) {
            String path = "%s/%s-system.md".formatted(directory, agent.name().toLowerCase(Locale.ROOT));
            prompts.put(agent, parse(path, read(path)));
        }
    }

    @Override
    public Prompt systemPrompt(AgentType agent) {
        return prompts.get(agent);
    }

    static Prompt parse(String path, String raw) {
        Matcher document = FRONT_MATTER.matcher(raw);
        if (!document.matches()) {
            throw new IllegalStateException("Prompt " + path + " must start with a front matter block");
        }
        Matcher version = VERSION.matcher(document.group("header"));
        if (!version.find()) {
            throw new IllegalStateException("Prompt " + path + " must declare a version");
        }
        String body = document.group("body").strip();
        if (body.isEmpty()) {
            throw new IllegalStateException("Prompt " + path + " is empty");
        }
        return new Prompt(version.group(1), body);
    }

    private static String read(String path) {
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            throw new IllegalStateException("Missing prompt " + path);
        }
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot read prompt " + path, ex);
        }
    }
}

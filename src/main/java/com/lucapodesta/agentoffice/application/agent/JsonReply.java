package com.lucapodesta.agentoffice.application.agent;

import com.lucapodesta.agentoffice.domain.exception.InvalidAgentOutputException;
import java.util.Locale;
import java.util.Optional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Tolerant reader for JSON produced by a language model: accepts Markdown code fences and
 * surrounding chatter, but is strict about the fields the pipeline depends on.
 */
final class JsonReply {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final String raw;
    private final JsonNode root;

    private JsonReply(String raw, JsonNode root) {
        this.raw = raw;
        this.root = root;
    }

    static JsonReply parse(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new InvalidAgentOutputException("Expected a JSON object but got: " + preview(text));
        }
        try {
            JsonNode node = MAPPER.readTree(text.substring(start, end + 1));
            if (!node.isObject()) {
                throw new InvalidAgentOutputException("Expected a JSON object but got: " + preview(text));
            }
            return new JsonReply(text, node);
        } catch (JacksonException ex) {
            throw new InvalidAgentOutputException("Malformed JSON: " + preview(text));
        }
    }

    /** The model's answer exactly as received, kept for the audit trail. */
    String raw() {
        return raw;
    }

    Optional<String> text(String field) {
        JsonNode value = root.get(field);
        if (value == null || value.isNull()) {
            return Optional.empty();
        }
        String text = value.isString() ? value.asString() : value.toString();
        return text.isBlank() ? Optional.empty() : Optional.of(text.strip());
    }

    String requiredText(String field) {
        return text(field).orElseThrow(() -> new InvalidAgentOutputException("Missing field '" + field + "'"));
    }

    <E extends Enum<E>> Optional<E> enumValue(String field, Class<E> type) {
        return text(field).map(value -> {
            try {
                return Enum.valueOf(type, value.toUpperCase(Locale.ROOT).replace(' ', '_'));
            } catch (IllegalArgumentException ex) {
                throw new InvalidAgentOutputException("Unknown %s '%s'".formatted(field, value));
            }
        });
    }

    private static String preview(String text) {
        String compact = text.strip().replaceAll("\\s+", " ");
        return compact.length() <= 120 ? compact : compact.substring(0, 120) + "...";
    }
}

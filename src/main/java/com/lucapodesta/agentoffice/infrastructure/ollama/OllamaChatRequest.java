package com.lucapodesta.agentoffice.infrastructure.ollama;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/** Body of {@code POST /api/chat}. */
@JsonInclude(JsonInclude.Include.NON_NULL)
record OllamaChatRequest(
        String model,
        List<Message> messages,
        boolean stream,
        String format,
        Boolean think,
        Map<String, Object> options) {

    record Message(String role, String content) {
    }
}

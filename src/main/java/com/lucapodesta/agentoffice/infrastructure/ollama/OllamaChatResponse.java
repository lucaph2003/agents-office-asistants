package com.lucapodesta.agentoffice.infrastructure.ollama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Relevant subset of the {@code /api/chat} response. Durations are in nanoseconds. */
@JsonIgnoreProperties(ignoreUnknown = true)
record OllamaChatResponse(
        String model,
        Message message,
        boolean done,
        @JsonProperty("done_reason") String doneReason,
        @JsonProperty("total_duration") Long totalDuration,
        @JsonProperty("prompt_eval_count") Integer promptEvalCount,
        @JsonProperty("eval_count") Integer evalCount) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Message(String role, String content) {
    }
}

package com.lucapodesta.agentoffice.application.agent;

/** A worker's deliverable: a short summary and the full Markdown content. */
public record WorkProduct(String summary, String content) {
}

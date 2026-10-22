package com.lucapodesta.agentoffice.application.agent;

/** Context for a new attempt after the reviewer asked for changes. */
public record Revision(String previousOutput, String feedback) {
}

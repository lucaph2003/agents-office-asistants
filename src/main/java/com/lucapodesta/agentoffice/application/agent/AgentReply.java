package com.lucapodesta.agentoffice.application.agent;

/**
 * What an agent produced: the parsed value the pipeline acts on, the full output to store,
 * and a short summary for humans.
 */
public record AgentReply<T>(T value, String output, String summary) {
}

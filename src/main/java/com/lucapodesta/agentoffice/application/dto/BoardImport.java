package com.lucapodesta.agentoffice.application.dto;

import java.util.List;
import java.util.UUID;

/**
 * @param failed cards that could not be imported this time; they stay in the inbox and are retried on the next sync
 */
public record BoardImport(List<UUID> taskIds, int failed) {
}

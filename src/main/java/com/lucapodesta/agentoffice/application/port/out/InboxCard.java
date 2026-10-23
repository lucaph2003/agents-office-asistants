package com.lucapodesta.agentoffice.application.port.out;

/** A card someone put in the board's inbox by hand; it may become a task. */
public record InboxCard(String id, String url, String title, String description) {
}

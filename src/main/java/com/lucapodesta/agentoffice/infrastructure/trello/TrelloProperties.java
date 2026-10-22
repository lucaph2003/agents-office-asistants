package com.lucapodesta.agentoffice.infrastructure.trello;

import com.lucapodesta.agentoffice.domain.task.TaskStatus;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * List ids are board-specific, so they always come from configuration.
 * Failed and cancelled tasks share the Done list; the card title tells them apart.
 */
@ConfigurationProperties("agent-office.trello")
public record TrelloProperties(
        boolean enabled,
        URI baseUrl,
        String apiKey,
        String token,
        String boardId,
        Duration connectTimeout,
        Duration readTimeout,
        Lists lists) {

    public record Lists(String inbox, String planned, String inProgress, String review, String blocked, String done) {

        String forStatus(TaskStatus status) {
            return switch (status) {
                case INBOX -> inbox;
                case PLANNED -> planned;
                case IN_PROGRESS -> inProgress;
                case REVIEW -> review;
                case BLOCKED -> blocked;
                case DONE, FAILED, CANCELLED -> done;
            };
        }
    }

    /** Fails startup with every missing setting at once instead of failing on the first card. */
    void requireComplete() {
        List<String> missing = new ArrayList<>();
        check(missing, "TRELLO_API_KEY", apiKey);
        check(missing, "TRELLO_TOKEN", token);
        check(missing, "TRELLO_BOARD_ID", boardId);
        check(missing, "TRELLO_LIST_INBOX", lists == null ? null : lists.inbox());
        check(missing, "TRELLO_LIST_PLANNED", lists == null ? null : lists.planned());
        check(missing, "TRELLO_LIST_IN_PROGRESS", lists == null ? null : lists.inProgress());
        check(missing, "TRELLO_LIST_REVIEW", lists == null ? null : lists.review());
        check(missing, "TRELLO_LIST_BLOCKED", lists == null ? null : lists.blocked());
        check(missing, "TRELLO_LIST_DONE", lists == null ? null : lists.done());
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Trello is enabled but not configured. Missing: " + String.join(", ", missing));
        }
    }

    private static void check(List<String> missing, String name, String value) {
        if (value == null || value.isBlank()) {
            missing.add(name);
        }
    }

    @Override
    public String toString() {
        return "TrelloProperties[enabled=%s, baseUrl=%s, boardId=%s, apiKey=***, token=***]".formatted(enabled, baseUrl, boardId);
    }
}

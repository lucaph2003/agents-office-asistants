package com.lucapodesta.agentoffice.infrastructure.trello;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.lucapodesta.agentoffice.application.port.out.BoardCard;
import com.lucapodesta.agentoffice.application.port.out.TaskBoardPort;
import com.lucapodesta.agentoffice.application.port.out.TaskCard;
import com.lucapodesta.agentoffice.domain.exception.TaskBoardException;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Trello REST API v1. Credentials travel in the Authorization header, never in URLs. */
class TrelloTaskBoardAdapter implements TaskBoardPort {

    private final RestClient restClient;
    private final TrelloProperties.Lists lists;

    TrelloTaskBoardAdapter(RestClient restClient, TrelloProperties.Lists lists) {
        this.restClient = restClient;
        this.lists = lists;
    }

    @Override
    public BoardCard createCard(TaskCard card) {
        CardResponse created = call("create card", () -> restClient.post()
                .uri("/1/cards")
                .body(Map.of(
                        "idList", lists.forStatus(card.task().status()),
                        "name", TrelloCardFormatter.name(card.task()),
                        "desc", TrelloCardFormatter.description(card),
                        "pos", "top"))
                .retrieve()
                .body(CardResponse.class));
        if (created == null || created.id() == null) {
            throw new TaskBoardException("Trello did not return the created card", null);
        }
        return new BoardCard(created.id(), created.shortUrl() != null ? created.shortUrl() : created.url(),
                card.task().status());
    }

    @Override
    public void updateCard(String cardId, TaskCard card) {
        call("update card", () -> restClient.put()
                .uri("/1/cards/{id}", cardId)
                .body(Map.of(
                        "idList", lists.forStatus(card.task().status()),
                        "name", TrelloCardFormatter.name(card.task()),
                        "desc", TrelloCardFormatter.description(card)))
                .retrieve()
                .toBodilessEntity());
    }

    @Override
    public void addComment(String cardId, String text) {
        call("comment", () -> restClient.post()
                .uri("/1/cards/{id}/actions/comments", cardId)
                .body(Map.of("text", text))
                .retrieve()
                .toBodilessEntity());
    }

    private static <T> T call(String operation, Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException ex) {
            throw new TaskBoardException("Trello %s failed with status %d".formatted(operation, ex.getStatusCode().value()), ex);
        } catch (RestClientException ex) {
            throw new TaskBoardException("Trello %s failed: %s".formatted(operation, ex.getClass().getSimpleName()), ex);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CardResponse(String id, String url, String shortUrl) {
    }
}

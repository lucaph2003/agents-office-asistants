package com.lucapodesta.agentoffice.infrastructure.trello;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.lucapodesta.agentoffice.application.port.out.BoardCard;
import com.lucapodesta.agentoffice.application.port.out.InboxCard;
import com.lucapodesta.agentoffice.application.port.out.TaskCard;
import com.lucapodesta.agentoffice.domain.exception.ErrorCode;
import com.lucapodesta.agentoffice.domain.exception.TaskBoardException;
import com.lucapodesta.agentoffice.domain.task.Task;
import com.lucapodesta.agentoffice.domain.task.TaskSource;
import com.lucapodesta.agentoffice.domain.task.TaskStatus;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

class TrelloTaskBoardAdapterTest {

    @RegisterExtension
    static WireMockExtension trello = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private static final TrelloProperties.Lists LISTS =
            new TrelloProperties.Lists("l-inbox", "l-planned", "l-progress", "l-review", "l-blocked", "l-done");

    private final Task task = Task.create("Java 21 plan", "Plan the migration", null, TaskSource.API, null, false,
            Instant.parse("2026-01-01T10:00:00Z"));

    @Test
    void createsCardInTheColumnOfItsStatus() {
        trello.stubFor(post("/1/cards").willReturn(okJson("""
                {"id": "c1", "url": "https://trello.com/c/long", "shortUrl": "https://trello.com/c/abc", "idList": "l-inbox"}
                """)));

        BoardCard card = adapter().createCard(new TaskCard(task, null, null));

        assertThat(card).isEqualTo(new BoardCard("c1", "https://trello.com/c/abc", TaskStatus.INBOX));
        trello.verify(postRequestedFor(urlEqualTo("/1/cards"))
                .withHeader("Authorization", equalTo("OAuth oauth_consumer_key=\"key-123\", oauth_token=\"token-456\""))
                .withRequestBody(matchingJsonPath("$.idList", equalTo("l-inbox")))
                .withRequestBody(matchingJsonPath("$.name", equalTo("Java 21 plan")))
                .withRequestBody(matchingJsonPath("$.desc", containing(
                        "**Task ID:** `" + task.id() + "`"))));
        assertThat(trello.getAllServeEvents()).noneMatch(event -> event.getRequest().getUrl().contains("token-456"));
    }

    @Test
    void finishedTasksGoToDoneWithTheirOutcomeInTheTitle() {
        trello.stubFor(put("/1/cards/c1").willReturn(ok()));
        task.cancel(Instant.parse("2026-01-01T11:00:00Z"));

        adapter().updateCard("c1", new TaskCard(task, null, null));

        trello.verify(putRequestedFor(urlEqualTo("/1/cards/c1"))
                .withRequestBody(matchingJsonPath("$.idList", equalTo("l-done")))
                .withRequestBody(matchingJsonPath("$.name", equalTo("[CANCELLED] Java 21 plan"))));
    }

    @Test
    void postsComments() {
        trello.stubFor(post("/1/cards/c1/actions/comments").willReturn(okJson("{\"id\": \"a1\"}")));

        adapter().addComment("c1", "🧭 Manager: Assigned to DEVELOPER");

        trello.verify(postRequestedFor(urlEqualTo("/1/cards/c1/actions/comments"))
                .withRequestBody(equalToJson("{\"text\": \"🧭 Manager: Assigned to DEVELOPER\"}")));
    }

    @Test
    void rejectedCredentialsAreReportedWithoutLeakingThem() {
        trello.stubFor(post("/1/cards").willReturn(aResponse().withStatus(401).withBody("invalid token token-456")));

        assertThatThrownBy(() -> adapter().createCard(new TaskCard(task, null, null)))
                .isInstanceOf(TaskBoardException.class)
                .hasMessage("Trello create card failed with status 401")
                .extracting("code").isEqualTo(ErrorCode.TRELLO_UNAVAILABLE);
    }

    @Test
    void slowOrBrokenTrelloIsReported() {
        trello.stubFor(put("/1/cards/c1").willReturn(ok().withFixedDelay(1_000)));
        trello.stubFor(post("/1/cards").willReturn(okJson("{\"name\": \"no id\"}")));

        assertThatThrownBy(() -> adapter().updateCard("c1", new TaskCard(task, null, null)))
                .isInstanceOf(TaskBoardException.class)
                .hasMessageStartingWith("Trello update card failed: ");
        assertThatThrownBy(() -> adapter().createCard(new TaskCard(task, null, null)))
                .hasMessage("Trello did not return the created card");
    }

    @Test
    void readsTheInboxColumn() {
        trello.stubFor(get(urlEqualTo(
                "/1/lists/l-inbox/cards?fields=id,name,desc,url,shortUrl")).willReturn(okJson("""
                [{"id": "c1", "name": "Ideas for a Valencia video", "desc": "3 minutes", "shortUrl": "https://trello.com/c/c1"},
                 {"id": "c2", "name": "No description", "desc": "", "url": "https://trello.com/c/c2/long"},
                 {"id": "c3", "name": "   "}]
                """)));

        assertThat(adapter().inboxCards()).containsExactly(
                new InboxCard("c1", "https://trello.com/c/c1", "Ideas for a Valencia video", "3 minutes"),
                new InboxCard("c2", "https://trello.com/c/c2/long", "No description", ""));
    }

    @Test
    void serverErrorsAreReported() {
        trello.stubFor(post("/1/cards/c1/actions/comments").willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> adapter().addComment("c1", "hi"))
                .hasMessage("Trello comment failed with status 503");
    }

    private static TrelloTaskBoardAdapter adapter() {
        TrelloProperties properties = new TrelloProperties(true, URI.create(trello.baseUrl()), "key-123", "token-456",
                "board-1", Duration.ofMillis(500), Duration.ofMillis(300), LISTS);
        return new TrelloTaskBoardAdapter(TrelloConfig.restClient(RestClient.builder(), properties), LISTS);
    }
}

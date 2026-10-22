package com.lucapodesta.agentoffice.infrastructure.trello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class TrelloPropertiesTest {

    @Test
    void reportsEveryMissingSettingAtOnce() {
        TrelloProperties properties = new TrelloProperties(true, URI.create("https://api.trello.com"), "key", " ",
                "board", Duration.ofSeconds(1), Duration.ofSeconds(1),
                new TrelloProperties.Lists("a", "b", null, "d", "e", ""));

        assertThatThrownBy(properties::requireComplete)
                .hasMessage("Trello is enabled but not configured. Missing: TRELLO_TOKEN, TRELLO_LIST_IN_PROGRESS, TRELLO_LIST_DONE");
    }

    @Test
    void neverPrintsCredentials() {
        TrelloProperties properties = new TrelloProperties(true, URI.create("https://api.trello.com"), "key-123",
                "token-456", "board", Duration.ofSeconds(1), Duration.ofSeconds(1), null);

        assertThat(properties.toString()).doesNotContain("key-123").doesNotContain("token-456");
    }
}

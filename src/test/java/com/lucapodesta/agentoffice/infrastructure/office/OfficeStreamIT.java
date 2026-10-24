package com.lucapodesta.agentoffice.infrastructure.office;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.lucapodesta.agentoffice.AbstractIntegrationTest;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

class OfficeStreamIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OfficeStreamBroadcaster broadcaster;

    @Test
    void streamsCommittedEventsToViewers() throws Exception {
        // MockMvcTester waits for async results, which a stream never has, so open it with plain MockMvc.
        MvcResult stream = mockMvc.perform(get("/api/v1/office/stream").accept(MediaType.TEXT_EVENT_STREAM)).andReturn();
        assertThat(stream.getRequest().isAsyncStarted()).isTrue();

        assertThat(mvc.post().uri("/api/v1/tasks").contentType(MediaType.APPLICATION_JSON).content("""
                {"title": "Office demo", "description": "Watch it walk around", "source": "API"}
                """)).hasStatus(201);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(stream.getResponse().getContentAsString())
                        .contains(":connected")
                        .contains("\"type\":\"TASK_CREATED\"")
                        .contains("\"actor\":\"user\""));
        stream.getRequest().getAsyncContext().complete();
        await().atMost(Duration.ofSeconds(5)).until(() -> broadcaster.viewerCount() == 0);
    }

    @Test
    void streamRequiresTheApiKey() {
        assertThat(mvc.get().uri("/api/v1/office/stream").header("X-Api-Key", "wrong"))
                .hasStatus(401);
    }
}

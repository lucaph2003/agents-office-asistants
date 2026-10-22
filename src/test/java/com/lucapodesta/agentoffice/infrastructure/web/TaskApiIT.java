package com.lucapodesta.agentoffice.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.lucapodesta.agentoffice.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class TaskApiIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void createdTaskCanBeReadBack() {
        MvcTestResult created = create("""
                {"title": "Prepare Java migration proposal",
                 "description": "Create a plan to migrate an application from Java 11 to Java 21",
                 "source": "TELEGRAM", "externalReference": "chat-42"}
                """);

        assertThat(created).hasStatus(201);
        String id = idOf(created);
        assertThat(created.getResponse().getHeader("Location")).endsWith("/api/v1/tasks/" + id);

        MvcTestResult fetched = mvc.get().uri("/api/v1/tasks/{id}", id).exchange();
        assertThat(fetched).hasStatusOk();
        assertThat(fetched).bodyJson().extractingPath("$.status").isEqualTo("INBOX");
        assertThat(fetched).bodyJson().extractingPath("$.priority").isEqualTo("MEDIUM");
        assertThat(fetched).bodyJson().extractingPath("$.source").isEqualTo("TELEGRAM");
        assertThat(fetched).bodyJson().extractingPath("$.externalReference").isEqualTo("chat-42");
        assertThat(fetched).bodyJson().extractingPath("$.createdAt").asString().matches("\\d{4}-\\d{2}-\\d{2}T.*Z");
    }

    @Test
    void listFiltersAndPaginatesNewestFirst() {
        create(task("first", "LOW"));
        create(task("second", "HIGH"));
        create(task("third", "HIGH"));

        MvcTestResult high = mvc.get().uri("/api/v1/tasks?priority=HIGH&size=1").exchange();

        assertThat(high).hasStatusOk();
        assertThat(high).bodyJson().extractingPath("$.totalElements").asNumber().isEqualTo(2);
        assertThat(high).bodyJson().extractingPath("$.totalPages").asNumber().isEqualTo(2);
        assertThat(high).bodyJson().extractingPath("$.content[0].title").isEqualTo("third");

        assertThat(mvc.get().uri("/api/v1/tasks?status=DONE"))
                .bodyJson().extractingPath("$.totalElements").asNumber().isEqualTo(0);
        assertThat(mvc.get().uri("/api/v1/tasks"))
                .bodyJson().extractingPath("$.totalElements").asNumber().isEqualTo(3);
    }

    @Test
    void cancellationIsAuditedAndFinal() {
        String id = idOf(create(task("to cancel", "LOW")));

        assertThat(mvc.post().uri("/api/v1/tasks/{id}/cancel", id))
                .hasStatusOk()
                .bodyJson().extractingPath("$.status").isEqualTo("CANCELLED");
        assertThat(mvc.post().uri("/api/v1/tasks/{id}/cancel", id))
                .hasStatus(409)
                .bodyJson().extractingPath("$.code").isEqualTo("INVALID_TASK_STATE");

        MvcTestResult events = mvc.get().uri("/api/v1/tasks/{id}/events", id).exchange();
        assertThat(events).bodyJson().extractingPath("$[*].type").asArray()
                .containsExactly("TASK_CREATED", "TASK_CANCELLED");
        assertThat(events).bodyJson().extractingPath("$[1].details").isEqualTo("from=INBOX");
        assertThat(jdbc.queryForObject("SELECT completed_at IS NOT NULL FROM tasks WHERE id = ?::uuid",
                Boolean.class, id)).isTrue();
    }

    @Test
    void unknownTaskReturns404WithUniformBody() {
        MvcTestResult result = mvc.get().uri("/api/v1/tasks/{id}/events", "00000000-0000-0000-0000-000000000000")
                .exchange();

        assertThat(result).hasStatus(404);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("TASK_NOT_FOUND");
        assertThat(result).bodyJson().extractingPath("$.correlationId").asString().isNotBlank();
    }

    private MvcTestResult create(String body) {
        return mvc.post().uri("/api/v1/tasks").contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }

    private static String task(String title, String priority) {
        return """
                {"title": "%s", "description": "Some work", "priority": "%s"}
                """.formatted(title, priority);
    }

    private static String idOf(MvcTestResult result) {
        try {
            return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}

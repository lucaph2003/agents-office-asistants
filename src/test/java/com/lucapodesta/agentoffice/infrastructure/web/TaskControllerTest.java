package com.lucapodesta.agentoffice.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.lucapodesta.agentoffice.application.service.TaskService;
import com.lucapodesta.agentoffice.domain.exception.InvalidTaskStateException;
import com.lucapodesta.agentoffice.domain.exception.TaskNotFoundException;
import com.lucapodesta.agentoffice.domain.task.TaskStatus;
import com.lucapodesta.agentoffice.infrastructure.observability.CorrelationIdFilter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest(TaskController.class)
class TaskControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private TaskService taskService;

    @Test
    void rejectsInvalidBodyWithFieldDetails() {
        MvcTestResult result = mvc.post().uri("/api/v1/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \" \"}")
                .exchange();

        assertThat(result).hasStatus(400);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_ERROR");
        assertThat(result).bodyJson().extractingPath("$.details").asArray()
                .containsExactly("description: must not be blank", "title: must not be blank");
    }

    @Test
    void rejectsUnknownEnumValueInBody() {
        assertThat(mvc.post().uri("/api/v1/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"t\", \"description\": \"d\", \"priority\": \"CRITICAL\"}"))
                .hasStatus(400)
                .bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void mapsMissingTaskTo404() {
        UUID id = UUID.randomUUID();
        when(taskService.get(id)).thenThrow(new TaskNotFoundException(id));

        MvcTestResult result = mvc.get().uri("/api/v1/tasks/{id}", id)
                .header(CorrelationIdFilter.HEADER, "test-correlation-1")
                .exchange();

        assertThat(result).hasStatus(404)
                .hasHeader(CorrelationIdFilter.HEADER, "test-correlation-1");
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("TASK_NOT_FOUND");
        assertThat(result).bodyJson().extractingPath("$.correlationId").isEqualTo("test-correlation-1");
        assertThat(result).bodyJson().extractingPath("$.timestamp").isEqualTo("2026-01-01T10:00:00Z");
    }

    @Test
    void mapsIllegalTransitionTo409() {
        UUID id = UUID.randomUUID();
        when(taskService.cancel(any())).thenThrow(new InvalidTaskStateException(id, TaskStatus.DONE, TaskStatus.CANCELLED));

        assertThat(mvc.post().uri("/api/v1/tasks/{id}/cancel", id))
                .hasStatus(409)
                .bodyJson().extractingPath("$.code").isEqualTo("INVALID_TASK_STATE");
    }

    @Test
    void rejectsMalformedIdAndFilters() {
        assertThat(mvc.get().uri("/api/v1/tasks/not-a-uuid"))
                .hasStatus(400).bodyJson().extractingPath("$.message").isEqualTo("Invalid value for parameter 'id'");
        assertThat(mvc.get().uri("/api/v1/tasks?status=SLEEPING"))
                .hasStatus(400).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_ERROR");
        assertThat(mvc.get().uri("/api/v1/tasks?size=500"))
                .hasStatus(400).bodyJson().extractingPath("$.details[0]").asString().startsWith("size:");
    }

    @Test
    void unexpectedFailureHidesInternals() {
        when(taskService.get(any())).thenThrow(new IllegalStateException("db password is hunter2"));

        assertThat(mvc.get().uri("/api/v1/tasks/{id}", UUID.randomUUID()))
                .hasStatus(500)
                .bodyJson().extractingPath("$.message").isEqualTo("Unexpected error");
    }

    @Test
    void unknownRouteUsesUniformBody() {
        assertThat(mvc.get().uri("/api/v1/nope"))
                .hasStatus(404)
                .bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
    }

    @Test
    void generatesCorrelationIdWhenHeaderIsUnsafe() {
        MvcTestResult result = mvc.get().uri("/api/v1/nope")
                .header(CorrelationIdFilter.HEADER, "bad id\nwith newline")
                .exchange();

        assertThat(result.getResponse().getHeader(CorrelationIdFilter.HEADER))
                .isNotBlank()
                .matches("[0-9a-f-]{36}");
    }

    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);
        }
    }
}

package com.lucapodesta.agentoffice.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "Uniform error body returned by every endpoint")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(
        @Schema(example = "TASK_NOT_FOUND") String code,
        @Schema(example = "Task 4f0c... was not found") String message,
        @Schema(example = "3f6d2a7e-9a51-4c43-9a7e-0c1f0b3d2c11") String correlationId,
        Instant timestamp,
        @Schema(description = "Field-level problems, present for validation errors",
                example = "[\"title: must not be blank\"]") List<String> details) {
}

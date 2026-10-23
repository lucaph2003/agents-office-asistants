package com.lucapodesta.agentoffice.infrastructure.web;

import com.lucapodesta.agentoffice.application.dto.BoardImport;
import com.lucapodesta.agentoffice.application.service.BoardIntake;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/board")
@ConditionalOnBooleanProperty("agent-office.trello.enabled")
@Tag(name = "Board", description = "Trello board as a task inbox")
class BoardController {

    private final BoardIntake intake;

    BoardController(BoardIntake intake) {
        this.intake = intake;
    }

    @PostMapping("/sync")
    @Operation(summary = "Import new inbox cards as tasks",
            description = "Every card in the inbox column that is not linked to a task yet becomes a task and starts "
                    + "running. Safe to call repeatedly; n8n calls it every minute.")
    @ApiResponse(responseCode = "200", description = "Cards imported")
    @ApiResponse(responseCode = "503", description = "TRELLO_UNAVAILABLE",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    SyncResponse sync() {
        BoardImport result = intake.importNewCards();
        return new SyncResponse(result.taskIds().size(), result.failed(), result.taskIds());
    }

    record SyncResponse(int imported, int failed, List<UUID> taskIds) {
    }
}

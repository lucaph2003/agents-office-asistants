package com.lucapodesta.agentoffice.infrastructure.office;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/office")
@Tag(name = "Office", description = "Live feed for the office view")
class OfficeStreamController {

    private final OfficeStreamBroadcaster broadcaster;

    OfficeStreamController(OfficeStreamBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream task events",
            description = "Server-sent events, one per committed audit event, from the moment of connecting.")
    SseEmitter stream() {
        return broadcaster.subscribe();
    }
}

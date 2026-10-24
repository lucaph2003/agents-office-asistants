package com.lucapodesta.agentoffice.infrastructure.office;

import com.lucapodesta.agentoffice.application.dto.TaskEventRecorded;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

/**
 * Fans committed audit events out to every open office view. A periodic comment keeps proxies
 * from closing idle streams and is how viewers that went away are noticed and dropped.
 */
class OfficeStreamBroadcaster implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(OfficeStreamBroadcaster.class);

    private final List<SseEmitter> viewers = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService heartbeat =
            Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().name("office-heartbeat").factory());

    OfficeStreamBroadcaster(Duration heartbeatInterval) {
        long millis = heartbeatInterval.toMillis();
        heartbeat.scheduleAtFixedRate(() -> broadcast(SseEmitter.event().comment("ping")),
                millis, millis, TimeUnit.MILLISECONDS);
    }

    /** Opens a stream that stays open until the viewer disconnects. */
    SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        subscribe(emitter);
        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException ex) {
            viewers.remove(emitter);
        }
        return emitter;
    }

    void subscribe(SseEmitter emitter) {
        emitter.onCompletion(() -> viewers.remove(emitter));
        emitter.onTimeout(() -> viewers.remove(emitter));
        emitter.onError(error -> viewers.remove(emitter));
        viewers.add(emitter);
    }

    int viewerCount() {
        return viewers.size();
    }

    @TransactionalEventListener(fallbackExecution = true)
    void on(TaskEventRecorded recorded) {
        OfficeEventMessage message = OfficeEventMessage.from(recorded.event());
        broadcast(SseEmitter.event().id(message.id().toString()).data(message, MediaType.APPLICATION_JSON));
    }

    /** Synchronized because pipelines and the heartbeat send concurrently and emitters are not thread-safe. */
    private synchronized void broadcast(SseEventBuilder event) {
        for (SseEmitter viewer : viewers) {
            try {
                viewer.send(event);
            } catch (IOException | IllegalStateException ex) {
                log.debug("event=OFFICE_VIEWER_DROPPED reason=\"{}\"", ex.getMessage());
                viewers.remove(viewer);
                viewer.completeWithError(ex);
            }
        }
    }

    @Override
    public void close() {
        heartbeat.shutdownNow();
        viewers.forEach(SseEmitter::complete);
        viewers.clear();
    }
}

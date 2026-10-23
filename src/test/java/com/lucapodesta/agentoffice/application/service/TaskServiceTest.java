package com.lucapodesta.agentoffice.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lucapodesta.agentoffice.application.dto.CreateTaskCommand;
import com.lucapodesta.agentoffice.application.dto.TaskEventRecorded;
import com.lucapodesta.agentoffice.application.dto.TaskExecutionRequestedEvent;
import com.lucapodesta.agentoffice.application.dto.TaskSubmittedEvent;
import com.lucapodesta.agentoffice.application.port.out.AgentExecutionRepository;
import com.lucapodesta.agentoffice.application.port.out.BoardCard;
import com.lucapodesta.agentoffice.application.port.out.BoardCardRepository;
import com.lucapodesta.agentoffice.application.port.out.InboxCard;
import com.lucapodesta.agentoffice.application.port.out.ReviewRepository;
import com.lucapodesta.agentoffice.application.port.out.TaskEventRepository;
import com.lucapodesta.agentoffice.application.port.out.TaskRepository;
import com.lucapodesta.agentoffice.domain.exception.InvalidTaskStateException;
import com.lucapodesta.agentoffice.domain.exception.TaskNotFoundException;
import com.lucapodesta.agentoffice.domain.task.Task;
import com.lucapodesta.agentoffice.domain.task.TaskEvent;
import com.lucapodesta.agentoffice.domain.task.TaskEventType;
import com.lucapodesta.agentoffice.domain.task.TaskPriority;
import com.lucapodesta.agentoffice.domain.task.TaskSource;
import com.lucapodesta.agentoffice.domain.task.TaskStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    @Mock
    private TaskRepository tasks;

    @Mock
    private TaskEventRepository events;

    @Mock
    private AgentExecutionRepository executions;

    @Mock
    private ReviewRepository reviews;

    @Mock
    private BoardCardRepository boardCards;

    @Mock
    private ApplicationEventPublisher publisher;

    private TaskService service;

    @BeforeEach
    void setUp() {
        service = new TaskService(tasks, events, new TaskEventRecorder(events, publisher), executions, reviews,
                boardCards, publisher, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createPersistsTaskAndRecordsCreation() {
        when(tasks.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Task task = service.create(new CreateTaskCommand("Title", "Description", TaskPriority.HIGH,
                TaskSource.TELEGRAM, "chat-42", false));

        assertThat(task.status()).isEqualTo(TaskStatus.INBOX);
        assertThat(task.createdAt()).isEqualTo(NOW);
        TaskEvent event = capturedEvent();
        assertThat(event.type()).isEqualTo(TaskEventType.TASK_CREATED);
        assertThat(event.taskId()).isEqualTo(task.id());
        assertThat(event.details()).isEqualTo("source=TELEGRAM priority=HIGH");
        verify(publisher).publishEvent(new TaskSubmittedEvent(task.id()));
        verify(publisher).publishEvent(new TaskEventRecorded(event));
    }

    @Test
    void executeResumesBlockedTaskAndSchedulesIt() {
        Task task = newTask();
        task.block(NOW);
        when(tasks.findById(task.id())).thenReturn(Optional.of(task));
        when(tasks.save(task)).thenReturn(task);

        Task resumed = service.execute(task.id());

        assertThat(resumed.status()).isEqualTo(TaskStatus.INBOX);
        assertThat(capturedEvent().type()).isEqualTo(TaskEventType.TASK_RESUMED);
        verify(publisher).publishEvent(new TaskExecutionRequestedEvent(task.id()));
    }

    @Test
    void executeSchedulesWaitingTaskWithoutChangingIt() {
        Task task = newTask();
        when(tasks.findById(task.id())).thenReturn(Optional.of(task));

        service.execute(task.id());

        verify(tasks, never()).save(any());
        verify(publisher).publishEvent(new TaskExecutionRequestedEvent(task.id()));
    }

    @Test
    void executeRefusesTasksAlreadyRunningOrFinished() {
        Task task = newTask();
        task.cancel(NOW);
        when(tasks.findById(task.id())).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.execute(task.id())).isInstanceOf(InvalidTaskStateException.class);
        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void importedBoardCardBecomesATaskLinkedToThatCard() {
        when(tasks.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Task task = service.importFromBoard(new InboxCard("card-7", "https://trello.com/c/7", "Ideas for a Valencia video",
                "Short, cinematic, 3 minutes"));

        assertThat(task.source()).isEqualTo(TaskSource.TRELLO);
        assertThat(task.externalReference()).isEqualTo("card-7");
        assertThat(task.title()).isEqualTo("Ideas for a Valencia video");
        assertThat(task.description()).isEqualTo("Short, cinematic, 3 minutes");
        verify(boardCards).save(task.id(), new BoardCard("card-7", "https://trello.com/c/7", TaskStatus.INBOX));
        verify(publisher).publishEvent(new TaskSubmittedEvent(task.id()));
    }

    @Test
    void importedCardWithoutDescriptionUsesItsTitleAndLongTitlesAreShortened() {
        when(tasks.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        String longName = "x".repeat(Task.MAX_TITLE_LENGTH + 50);

        Task task = service.importFromBoard(new InboxCard("card-8", "https://trello.com/c/8", longName, "  "));

        assertThat(task.title()).hasSize(Task.MAX_TITLE_LENGTH).endsWith("…");
        assertThat(task.description()).isEqualTo(longName);
    }

    @Test
    void getThrowsWhenTaskIsMissing() {
        UUID id = UUID.randomUUID();
        when(tasks.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id)).isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    void cancelRecordsPreviousStatus() {
        Task task = newTask();
        when(tasks.findById(task.id())).thenReturn(Optional.of(task));
        when(tasks.save(task)).thenReturn(task);

        Task cancelled = service.cancel(task.id());

        assertThat(cancelled.status()).isEqualTo(TaskStatus.CANCELLED);
        TaskEvent event = capturedEvent();
        assertThat(event.type()).isEqualTo(TaskEventType.TASK_CANCELLED);
        assertThat(event.details()).isEqualTo("from=INBOX");
    }

    @Test
    void cancelOfFinishedTaskRecordsNothing() {
        Task task = newTask();
        task.cancel(NOW);
        when(tasks.findById(task.id())).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.cancel(task.id())).isInstanceOf(InvalidTaskStateException.class);
        verify(tasks, never()).save(any());
        verify(events, never()).append(any());
    }

    @Test
    void historyRequiresExistingTask() {
        UUID id = UUID.randomUUID();
        when(tasks.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.history(id)).isInstanceOf(TaskNotFoundException.class);
        verify(events, never()).findByTaskId(any());
    }

    private TaskEvent capturedEvent() {
        ArgumentCaptor<TaskEvent> captor = ArgumentCaptor.forClass(TaskEvent.class);
        verify(events).append(captor.capture());
        return captor.getValue();
    }

    private static Task newTask() {
        return Task.create("Title", "Description", null, TaskSource.API, null, false, NOW);
    }
}

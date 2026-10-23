package com.lucapodesta.agentoffice.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lucapodesta.agentoffice.application.dto.BoardImport;
import com.lucapodesta.agentoffice.application.port.out.BoardCard;
import com.lucapodesta.agentoffice.application.port.out.InboxCard;
import com.lucapodesta.agentoffice.application.port.out.TaskBoardPort;
import com.lucapodesta.agentoffice.application.support.InMemoryStore;
import com.lucapodesta.agentoffice.domain.task.Task;
import com.lucapodesta.agentoffice.domain.task.TaskSource;
import com.lucapodesta.agentoffice.domain.task.TaskStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class BoardIntakeTest {

    private final InMemoryStore store = new InMemoryStore();
    private final TaskBoardPort board = mock(TaskBoardPort.class);
    private final TaskService tasks = mock(TaskService.class);
    private final BoardIntake intake = new BoardIntake(board, store.boardCardRepository, tasks);

    @Test
    void importsOnlyCardsThatAreNotTasksYet() {
        store.boardCardRepository.save(UUID.randomUUID(), new BoardCard("known", "https://trello.com/c/known", TaskStatus.INBOX));
        InboxCard fresh = card("fresh");
        when(board.inboxCards()).thenReturn(List.of(card("known"), fresh));
        Task created = task();
        when(tasks.importFromBoard(fresh)).thenReturn(created);

        BoardImport result = intake.importNewCards();

        assertThat(result.taskIds()).containsExactly(created.id());
        assertThat(result.failed()).isZero();
        verify(tasks, times(1)).importFromBoard(any());
    }

    @Test
    void oneBadCardDoesNotStopTheOthers() {
        InboxCard broken = card("broken");
        InboxCard fine = card("fine");
        when(board.inboxCards()).thenReturn(List.of(broken, fine));
        when(tasks.importFromBoard(broken)).thenThrow(new IllegalStateException("duplicate card"));
        when(tasks.importFromBoard(fine)).thenReturn(task());

        BoardImport result = intake.importNewCards();

        assertThat(result.taskIds()).hasSize(1);
        assertThat(result.failed()).isEqualTo(1);
    }

    @Test
    void largeInboxesAreImportedInBatches() {
        when(board.inboxCards()).thenReturn(IntStream.range(0, 30).mapToObj(i -> card("c" + i)).toList());
        when(tasks.importFromBoard(any())).thenAnswer(invocation -> task());

        assertThat(intake.importNewCards().taskIds()).hasSize(BoardIntake.MAX_CARDS_PER_SYNC);
    }

    @Test
    void emptyInboxDoesNothing() {
        when(board.inboxCards()).thenReturn(List.of());

        assertThat(intake.importNewCards().taskIds()).isEmpty();
        verify(tasks, never()).importFromBoard(any());
    }

    private static InboxCard card(String id) {
        return new InboxCard(id, "https://trello.com/c/" + id, "Task " + id, null);
    }

    private static Task task() {
        return Task.create("t", "d", null, TaskSource.TRELLO, "c", false, Instant.now());
    }
}

package com.lucapodesta.agentoffice.application.service;

import com.lucapodesta.agentoffice.application.dto.BoardImport;
import com.lucapodesta.agentoffice.application.port.out.BoardCardRepository;
import com.lucapodesta.agentoffice.application.port.out.InboxCard;
import com.lucapodesta.agentoffice.application.port.out.TaskBoardPort;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Picks up cards that were added to the board's inbox by hand (for example from the Trello app
 * while this machine was off) and turns each one into a task. Cards that already belong to a
 * task are skipped, so running it repeatedly is safe.
 */
public class BoardIntake {

    private static final Logger log = LoggerFactory.getLogger(BoardIntake.class);

    /** Keeps one sync call short; the rest are picked up by the next one. */
    static final int MAX_CARDS_PER_SYNC = 20;

    private final TaskBoardPort board;
    private final BoardCardRepository cards;
    private final TaskService tasks;

    public BoardIntake(TaskBoardPort board, BoardCardRepository cards, TaskService tasks) {
        this.board = board;
        this.cards = cards;
        this.tasks = tasks;
    }

    public BoardImport importNewCards() {
        List<InboxCard> inbox = board.inboxCards();
        Set<String> linked = cards.linkedCardIds(inbox.stream().map(InboxCard::id).toList());
        List<UUID> imported = new ArrayList<>();
        int failed = 0;
        for (InboxCard card : inbox) {
            if (linked.contains(card.id())) {
                continue;
            }
            if (imported.size() + failed >= MAX_CARDS_PER_SYNC) {
                break;
            }
            try {
                UUID taskId = tasks.importFromBoard(card).id();
                imported.add(taskId);
                log.info("event=BOARD_CARD_IMPORTED cardId={} taskId={}", card.id(), taskId);
            } catch (RuntimeException ex) {
                failed++;
                log.warn("event=BOARD_CARD_IMPORT_FAILED cardId={} reason=\"{}\"", card.id(), ex.getMessage());
            }
        }
        return new BoardImport(imported, failed);
    }
}

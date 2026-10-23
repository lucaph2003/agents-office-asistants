# 008 · The Trello inbox as a durable entry point

**Status:** accepted · 2026-10

## Context

Everything runs on one laptop. When it is off, Telegram keeps undelivered bot messages for at most 24 hours, and a
quick tunnel changes its URL on restart, so tasks sent while away can be lost without notice. Trello, on the other hand,
is always online and keeps cards indefinitely, and its mobile app is already in the user's pocket.

## Decision

The board's Inbox column becomes a second way to create tasks, next to Telegram:

- `BoardIntake` (application layer) lists the Inbox cards through `TaskBoardPort` and turns every card that is not
  linked to a task yet into one, with source `TRELLO` and the card id as external reference.
- The task and its link to **that same card** are written in one transaction, so the board sync moves the user's
  card instead of creating a second one. `card_id` is unique in `task_board_cards`, which makes importing the same card
  twice impossible even if two syncs overlap.
- n8n triggers the import every minute through `POST /api/v1/board/sync`; the logic stays in the core
  ([ADR 002](002-n8n-as-external-orchestrator.md)). Each call handles at most 20 cards.
- Results of Trello tasks are sent to the owner's chat (`TELEGRAM_DEFAULT_CHAT_ID`), and `/tasks` from that chat
  lists every task.

## Consequences

- Tasks can be queued for days while the machine is off and run as soon as it is back.
- PostgreSQL is still the source of truth: only new Inbox cards are read; moving or editing existing cards by hand
  has no effect.
- Every unlinked card in the Inbox becomes a task, so the board should be dedicated to Agent Office. A label filter
  would be the next step if that becomes a problem.
- The card's original description is kept as the task description and shown in the card's *Description* section,
  while the rest of the card is rewritten with the task's state.

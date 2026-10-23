# 004 · Trello as a view of the tasks, not their source of truth

**Status:** accepted · 2026-10

## Context

Trello gives a ready-made visual office for the demo and for daily use. But it is an external service with rate
limits and outages, and users can move cards by hand.

## Decision

- PostgreSQL is the source of truth; the board only mirrors it. Moving a card by hand does not change the task.
- The link between task and card lives in its own table (`task_board_cards`) so board sync never competes with the
  pipeline for the task row and its optimistic lock.
- A `TaskBoardSynchronizer` (application layer) reacts to committed audit events: it creates the card on first sight,
  moves it with the task's status and comments on meaningful steps. Each update renders the full current state, so a
  failed call is repaired by the next event.
- Trello failures are recorded as `TRELLO_SYNC_FAILED` and never affect the task. List ids come from configuration;
  `scripts/trello-setup.sh` creates the board and prints them.
- Credentials travel in the `Authorization` header, never in URLs.

## Consequences

- The pipeline works with Trello disabled or down; the board catches up on the next change.
- The only Trello → Agent Office direction is the inbox: new cards there become tasks
  ([ADR 008](008-trello-inbox-as-durable-intake.md)). Moving existing cards by hand still changes nothing.
- Creating a task waits for the card to be created (bounded by a 5 s timeout), which lets the API response, and the
  Telegram reply, include the card link.

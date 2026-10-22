# 007 · Integrations react to committed audit events

**Status:** accepted · 2026-10

## Context

The board and the notifications must follow every task change, but they are slow, external and can fail. Calling
them from inside the pipeline would couple the pipeline to every integration and risk acting on changes that are later
rolled back.

## Decision

Every audit event is published as an application event when it is recorded. Integrations listen with
`@TransactionalEventListener`, so they only see changes that were committed:

1. Board sync runs first, synchronously on the thread that made the change, which keeps a task's updates in order and
   guarantees the card exists before the pipeline starts.
2. Notifications run next, so they can link to an up-to-date card.
3. Metrics count the same events.

Each integration reads and writes in its own transaction (the original one has already committed) and records its own
outcome (`TRELLO_UPDATED`, `USER_NOTIFIED`, `…_FAILED`) in the audit trail.

## Consequences

- The pipeline knows nothing about Trello, n8n or metrics; a new integration is a new listener.
- Events are in-process and not durable: if the application stops between a commit and its listener, that board update
  or notification is lost. The board heals on the next event; a lost notification does not. An outbox table would
  close this gap and is listed for V2.

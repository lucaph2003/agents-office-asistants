# 005 · Tasks, executions and reviews as separate records

**Status:** accepted · 2026-10

## Context

A task may take several attempts; each attempt involves several model calls; reviews can send work back. It must be
possible to reconstruct exactly what happened, and agent loops must always terminate.

## Decision

- **Task**: the unit of work and its lifecycle, guarded by an explicit state machine
  (`INBOX → PLANNED → IN_PROGRESS → REVIEW → DONE`, plus `BLOCKED`, `FAILED`, `CANCELLED`). Illegal transitions throw.
- **AgentExecution**: one record per agent call (Manager, worker, Reviewer) with model, prompt version, attempt,
  status, a short summary and the output, or the error code.
- **Review**: the verdict on a specific worker execution (`APPROVED`, `CHANGES_REQUESTED`, `REJECTED`), by an agent or,
  later, a human.
- **TaskEvent**: an append-only audit trail with a database sequence for ordering. Not event sourcing; state is not
  rebuilt from it.
- **ReviewPolicy** decides the outcome of a review and owns the attempt limit (3 by default), which is what bounds the
  worker ↔ reviewer loop. `requiresHumanApproval` makes an approved task wait in `REVIEW` instead of completing.
- The pipeline runs in the background after the creating request commits. Model calls never hold a transaction; each
  state change re-reads the task and applies only if it is still where the pipeline left it.
- An unreachable model blocks the task (resumable with `POST /execute`); unusable or rejected output fails it.

## Consequences

- `GET /tasks/{id}/executions`, `/reviews` and `/events` explain every task without reading logs.
- Cancelling a task while a model is thinking simply stops the pipeline at the next step.
- In-flight work is not persisted as a job: after a crash, a task stuck in `IN_PROGRESS` needs attention. A durable
  job table or outbox is a V2 item.
- An attempt that fails on infrastructure still counts towards the limit, which keeps the rule simple.

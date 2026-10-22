# 002 · n8n as the external orchestrator, not the brain

**Status:** accepted · 2026-10

## Context

n8n makes Telegram, schedules and webhooks trivial and gives a visual view of each integration. It is also tempting
to put business logic in it: routing rules, retries, state. Logic spread across visual workflows is hard to test,
review and version.

## Decision

n8n only translates between the outside world and the REST API:

- **Telegram intake:** parse the command, call the API, format the reply.
- **Notifications:** receive Agent Office's webhook, format the message, send it to the chat.

Everything with rules lives in Spring Boot: task lifecycle, agent routing, retries, review, audit. Spring Boot never
talks to Telegram; it calls an n8n webhook through a `NotificationPort`, and n8n picks the channel.

The workflows are versioned as JSON in `n8n/`, their Code-node logic is unit-tested with Node, and both directions are
authenticated (`X-Api-Key` towards the API, `X-Agent-Office-Token` towards n8n).

## Consequences

- Replacing Telegram with Slack is a new n8n workflow; the core does not change.
- The API is the contract, so any other client (curl, a future web UI) gets the same behaviour.
- The Telegram Trigger uses webhooks, so n8n needs a public HTTPS URL (a tunnel) even in a local setup.
- Credentials live in n8n and are never versioned; a template with matching ids makes importing them one command.

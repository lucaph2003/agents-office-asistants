# 001 · Hexagonal architecture, pragmatically

**Status:** accepted · 2026-10

## Context

Agent Office talks to four external systems (PostgreSQL, Ollama, Trello, n8n) and is expected to swap some of
them: another LLM provider, another board, another chat channel. The core rules (task lifecycle, retry limit,
review outcomes) must be testable without any of them running.

## Decision

Three packages with dependencies pointing inwards:

- `domain`: plain Java. Task state machine, executions, reviews, audit events, review policy. No Spring, JPA or Jackson.
- `application`: use cases (`TaskService`, `TaskPipeline`), agents, and **outbound ports** for everything external
  (`LanguageModelPort`, `TaskBoardPort`, `NotificationPort`, repositories).
- `infrastructure`: adapters implementing those ports, plus REST, security, configuration and observability.

Pragmatic choices:

- No inbound port interfaces: controllers call application services directly. An interface with a single
  implementation and a single caller adds nothing.
- The application layer uses Spring for `@Service`, `@Transactional` and event publishing. Only the domain is kept
  framework-free.
- JPA entities are separate from domain objects and mapped in the adapter, so persistence concerns (`@Version`,
  column types) never leak into the model.

The rules are enforced by an ArchUnit test, not by convention.

## Consequences

- The pipeline is tested end to end with in-memory fakes in milliseconds; integration tests are reserved for the adapters.
- Adding OpenAI, Slack or GitHub Projects means one new adapter; nothing in `domain` or `application` changes.
- There is mapping code between entities and domain objects. It is small and deliberate.

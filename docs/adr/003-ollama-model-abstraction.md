# 003 · Local models behind a provider-agnostic port

**Status:** accepted · 2026-10

## Context

The system should run on local models (privacy, cost, offline use) but not be tied to Ollama. Local models are also
less reliable at following output formats than hosted ones.

## Decision

- Agents depend on `LanguageModelPort` (`generate`, `modelFor`). `OllamaLanguageModelAdapter` is the only
  implementation; it chooses the model per agent from configuration and reports it back, so the application never
  knows model names.
- Transport behaviour is explicit: connect and read timeouts, a bounded retry for connection failures and 5xx, no retry
  for timeouts (the model already used its budget), a missing model reported as `AGENT_NOT_AVAILABLE` with the exact
  `ollama pull` command.
- Prompts are Markdown files with a versioned front matter, loaded and validated at startup. The prompt version and
  the model are stored with every execution.
- Every prompt asks for JSON, and answers are parsed tolerantly (code fences, chatter, case) but validated strictly.
  An unusable answer is retried once and then fails the task with `INVALID_AGENT_OUTPUT`. Model output is never trusted
  blindly: the Manager cannot route to itself or to the Reviewer, a negative review needs feedback.
- Task content is wrapped in `<task>` tags and prompts tell the model to treat it as data, a basic defence against
  prompt injection through task descriptions.

## Consequences

- An OpenAI, Claude or Gemini adapter is one class plus configuration.
- Each execution can be explained after the fact: which model, which prompt version, what it answered.
- JSON mode (`format: json`) constrains syntax, not shape; schema-constrained output is a possible V2 improvement.

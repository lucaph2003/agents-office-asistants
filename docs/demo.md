# Demo guide

A five-minute run that shows the whole flow: a message in Telegram becomes a card in Trello, the agents work on it
with a local model, and the result comes back to Telegram.

## Before recording

1. Everything configured once: `.env`, Trello board ([setup script](../scripts/trello-setup.sh)), Telegram bot,
   tunnel and n8n workflows ([n8n guide](n8n.md)).
2. Start the stack and check it is healthy:

   ```bash
   docker compose up --build -d --wait
   ```

3. Warm the model up so the first answer is not slowed down by loading it into memory:

   ```bash
   ollama run qwen3:8b "ready?" > /dev/null
   ```

4. Arrange the screen: Telegram on one side, the Trello board on the other, and a terminal following the logs:

   ```bash
   docker compose logs -f agent-office-core | grep -E 'event=(TASK_|REVIEW_|CHANGES|LLM_CALL)'
   ```

## The run

| # | Action | What to show |
|---|---|---|
| 1 | In Telegram: `/task Prepare a technical plan to migrate service X from Java 11 to Java 21` | The bot answers *Task created* with the id and the Trello link |
| 2 | Switch to Trello | A new card in **📥 Inbox**, with id, status, priority and description |
| 3 | Wait a few seconds | The card moves to **📋 Planned**; comment *🧭 Manager: Assigned to DEVELOPER …* |
| 4 | Keep watching (≈30 s with qwen3:8b on Apple Silicon) | **🤖 In Progress**, then the comment *✅ Developer finished: …*; in the terminal, `LLM_CALL_COMPLETED agent=DEVELOPER` with duration and tokens |
| 5 | | **👀 Review**, then *👍 Reviewer approved: …* |
| 6 | | The card lands in **✅ Done** with the latest execution and review in its description |
| 7 | Back to Telegram | *Task completed* with agent, review verdict, summary and the card link |

Wording matters: "Prepare a technical plan…" goes to the Developer, while "Analyze whether we should…" is usually routed
to Research. Both make a good demo; pick the one the video is about.

## Showing the internals

After the run, the same task from the API side (the id is in the Telegram reply):

```bash
export AGENT_OFFICE_API_KEY=...   # from .env
curl -s -H "X-Api-Key: $AGENT_OFFICE_API_KEY" localhost:8080/api/v1/tasks/<id>/executions | jq '.[] | {agentType, attempt, status, model, promptVersion, summary}'
curl -s -H "X-Api-Key: $AGENT_OFFICE_API_KEY" localhost:8080/api/v1/tasks/<id>/events | jq -r '.[].type'
```

- Every agent call with model and prompt version.
- The full audit trail, including `TRELLO_UPDATED` and `USER_NOTIFIED`.
- `grep telegram-<update_id>` in the logs follows the same message through API, agents, Trello and n8n.

Swagger UI at <http://localhost:8080/swagger-ui.html> (authorize with the API key) is a good last shot.

## Showing failure handling

- **Model down:** stop Ollama and send a task. The card goes to **⛔ Blocked** and Telegram explains why. Start Ollama
  again and send `/retry <id>`: the task resumes and finishes.
- **Cancel:** send a task and `/cancel <id>` while it is in progress; the pipeline stops at the next step and the card
  ends as `[CANCELLED]`.

## Without Telegram or Trello

Leave `TRELLO_ENABLED=false` and skip n8n: the same flow runs through the API alone.

```bash
curl -s -X POST localhost:8080/api/v1/tasks \
  -H "X-Api-Key: $AGENT_OFFICE_API_KEY" -H 'Content-Type: application/json' \
  -d '{"title": "Java 21 migration plan", "description": "Prepare a technical plan to migrate service X from Java 11 to Java 21"}'
```

Then poll `GET /api/v1/tasks/<id>` until `DONE` and show executions and events as above.

## Screenshots worth keeping

Save them in `docs/images/` and link them from the README's Demo section:

1. Telegram conversation: `/task`, *Task created*, *Task completed*.
2. Trello board with cards in several columns, and one card opened showing its description and comments.
3. The two n8n workflows in the editor.
4. Swagger UI.

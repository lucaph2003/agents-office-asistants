# Manual de instalación

Guía rápida para tener Agent Office andando en tu PC. Los scripts de arranque están pensados para **macOS**; en Linux/Windows funciona igual con Docker, pero los pasos de "arranque automático" se hacen a mano (ver el final).

## 1. Requisitos

| Qué | Para qué | Instalación (macOS) |
|---|---|---|
| Docker (OrbStack o Docker Desktop) | Base de datos, app y n8n | `brew install orbstack` |
| [Ollama](https://ollama.com) | Modelos de IA locales | `brew install ollama` |
| cloudflared | Túnel HTTPS para Telegram | `brew install cloudflared` |
| Git | Bajar el proyecto | `xcode-select --install` |

Hardware: con `qwen3:8b` alcanza con 16 GB de RAM. Con menos, usá un modelo más chico (ej. `qwen3:4b`).

## 2. Bajar el proyecto y el modelo

```bash
git clone https://github.com/lucaph2003/agents-office-asistants.git
cd agents-office-asistants
ollama pull qwen3:8b
```

## 3. Configurar `.env`

```bash
cp .env.example .env
```

Editá `.env` y cambiá como mínimo:

- `AGENT_OFFICE_API_KEY` → generá uno con `openssl rand -hex 24`
- `N8N_WEBHOOK_TOKEN` → otro valor con el mismo comando

Con eso ya podés usar la app sola (sin Telegram ni Trello).

## 4. Primer arranque

```bash
scripts/start.sh --build
```

Cuando termina:

- API (Swagger): <http://localhost:8080/swagger-ui.html>
- n8n: <http://localhost:5678> (la primera vez te pide crear un usuario local)

Prueba rápida:

```bash
source .env
curl -X POST localhost:8080/api/v1/tasks \
  -H "X-Api-Key: $AGENT_OFFICE_API_KEY" -H 'Content-Type: application/json' \
  -d '{"title": "Prueba", "description": "Escribí un plan corto para aprender Docker"}'
```

## 5. Telegram (opcional)

1. En Telegram hablale a [@BotFather](https://t.me/BotFather), mandá `/newbot` y guardá el token.
2. Credenciales de n8n:
   ```bash
   cp n8n/credentials/credentials.example.json n8n/credentials/credentials.json
   ```
   Completá en ese archivo: token del bot, el valor de `AGENT_OFFICE_API_KEY` y el de `N8N_WEBHOOK_TOKEN`.
3. Poné `N8N_NOTIFICATIONS_ENABLED=true` en `.env`.
4. Importá y publicá los workflows:
   ```bash
   docker compose exec n8n n8n import:credentials --input=/workflows/credentials/credentials.json
   docker compose exec n8n n8n import:workflow --separate --input=/workflows
   docker compose exec n8n n8n publish:workflow --id=agentOfficeTaskCompleted
   docker compose exec n8n n8n publish:workflow --id=agentOfficeTelegramIntake
   ```
5. Reiniciá todo con `scripts/start.sh` (levanta el túnel y le avisa a Telegram la URL nueva).
6. Mandale `/start` al bot: te responde con tu **chat id**. Ponelo en `TELEGRAM_ALLOWED_CHAT_IDS` y `TELEGRAM_DEFAULT_CHAT_ID` del `.env` y corré `scripts/start.sh` de nuevo.

Listo: `/task <lo que quieras>` en el bot crea una tarea.

## 6. Trello (opcional)

1. Sacá una API key y un token en <https://trello.com/power-ups/admin>.
2. Creá el tablero con sus listas:
   ```bash
   TRELLO_API_KEY=tu-key TRELLO_TOKEN=tu-token scripts/trello-setup.sh
   ```
3. Pegá en `.env` las líneas `TRELLO_*` que imprime y poné `TRELLO_ENABLED=true`.
4. Publicá el workflow de sincronización y reiniciá:
   ```bash
   docker compose exec n8n n8n publish:workflow --id=agentOfficeTrelloSync
   scripts/start.sh
   ```

Las tarjetas que agregues a la lista **Inbox** se convierten en tareas.

## 7. Uso diario

```bash
scripts/start.sh          # prender (agregá --build si cambiaste código)
scripts/stop.sh           # apagar (agregá --all para cerrar también Ollama y OrbStack)
```

Los datos se guardan en volúmenes de Docker: apagar no borra nada.

## Problemas comunes

| Síntoma | Solución |
|---|---|
| `No .env found` | Te faltó el paso 3 |
| El bot no responde | Corré `scripts/start.sh` de nuevo (la URL del túnel cambia en cada arranque) y revisá que tu chat id esté en `TELEGRAM_ALLOWED_CHAT_IDS` |
| Las tareas tardan mucho o dan timeout | Usá un modelo más chico en `OLLAMA_MODEL` o subí `OLLAMA_READ_TIMEOUT` |
| No llegan los resultados a Telegram | Volvé a publicar los workflows (importar los despublica) |
| Ver qué pasa | `docker compose logs -f agent-office-core` |

## Linux / Windows

`start.sh` y `stop.sh` usan apps de macOS. En otros sistemas:

1. Abrí Docker y Ollama a mano.
2. Si usás Telegram: `cloudflared tunnel --url http://localhost:5678`, copiá la URL `https://…` a `N8N_WEBHOOK_URL` en `.env` y volvé a publicar el workflow de Telegram.
3. `docker compose up --build -d` para prender y `docker compose stop` para apagar.

Más detalle técnico en el [README](../README.md) y en [docs/n8n.md](n8n.md).

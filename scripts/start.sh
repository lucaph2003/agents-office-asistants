#!/usr/bin/env bash
# Starts everything Agent Office needs on a Mac: OrbStack/Docker, Ollama, the HTTPS tunnel for
# Telegram and the Docker Compose stack. Safe to run when things are already up.
#
# Usage: scripts/start.sh [--build]     --build rebuilds the app image first
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
RUN_DIR="$ROOT/.run"
mkdir -p "$RUN_DIR"

step() { printf '\n▶ %s\n' "$1"; }
fail() { printf '✖ %s\n' "$1" >&2; exit 1; }

wait_for() {  # wait_for <seconds> <description> <command...>
  local seconds=$1 what=$2; shift 2
  for _ in $(seq 1 "$seconds"); do
    if "$@" >/dev/null 2>&1; then return 0; fi
    sleep 1
  done
  fail "$what did not come up within ${seconds}s"
}

[[ -f .env ]] || fail "No .env found. Copy .env.example to .env first."

step "Docker"
if ! docker info >/dev/null 2>&1; then
  open -ga OrbStack 2>/dev/null || fail "Docker is not running and OrbStack could not be opened"
  wait_for 120 "Docker" docker info
fi
echo "  Docker is running"

step "Ollama"
if ! curl -sf -m 2 http://localhost:11434/api/version >/dev/null; then
  open -ga Ollama 2>/dev/null || fail "Ollama is not running and could not be opened"
  wait_for 60 "Ollama" curl -sf -m 2 http://localhost:11434/api/version
fi
echo "  Ollama is running"

step "Tunnel for Telegram"
if command -v cloudflared >/dev/null 2>&1; then
  if [[ -f "$RUN_DIR/tunnel.pid" ]] && kill -0 "$(cat "$RUN_DIR/tunnel.pid")" 2>/dev/null; then
    kill "$(cat "$RUN_DIR/tunnel.pid")"
  fi
  : > "$RUN_DIR/tunnel.log"
  nohup cloudflared tunnel --no-autoupdate --url http://localhost:5678 > "$RUN_DIR/tunnel.log" 2>&1 &
  echo $! > "$RUN_DIR/tunnel.pid"
  wait_for 60 "Tunnel" grep -qE 'https://[a-z0-9-]+\.trycloudflare\.com' "$RUN_DIR/tunnel.log"
  TUNNEL_URL="$(grep -oE 'https://[a-z0-9-]+\.trycloudflare\.com' "$RUN_DIR/tunnel.log" | head -1)/"
  # Each quick tunnel gets a new URL; n8n registers it with Telegram when it starts.
  perl -pi -e "s|^N8N_WEBHOOK_URL=.*|N8N_WEBHOOK_URL=${TUNNEL_URL}|" .env
  echo "  $TUNNEL_URL"
else
  echo "  cloudflared not installed: Telegram will not receive messages (brew install cloudflared)"
fi

step "Agent Office stack"
if [[ "${1:-}" == "--build" ]]; then
  docker compose up --build -d --wait
else
  docker compose up -d --wait
fi

CREDENTIALS="n8n/credentials/credentials.json"
if [[ -n "${TUNNEL_URL:-}" && -f "$CREDENTIALS" ]]; then
  step "Telegram webhook"
  # n8n only registers the bot webhook when the workflow is activated from a running instance,
  # not on startup, so point Telegram at the new tunnel directly. URL and secret token follow
  # n8n's Telegram Trigger conventions and are derived from the exported workflow.
  python3 - "$TUNNEL_URL" "$CREDENTIALS" <<'PY'
import json, sys, urllib.parse, urllib.request
base, credentials = sys.argv[1].rstrip("/"), sys.argv[2]
workflow = json.load(open("n8n/telegram-task-intake.json"))
trigger = next(node for node in workflow["nodes"] if node["type"] == "n8n-nodes-base.telegramTrigger")
token = {c["id"]: c for c in json.load(open(credentials))}["agent-office-telegram"]["data"]["accessToken"]
body = urllib.parse.urlencode({
    "url": f"{base}/webhook/{trigger['webhookId']}/webhook",
    "secret_token": "".join(ch for ch in f"{workflow['id']}_{trigger['id']}" if ch.isalnum() or ch in "_-"),
    "allowed_updates": json.dumps(trigger["parameters"]["updates"]),
}).encode()
with urllib.request.urlopen(f"https://api.telegram.org/bot{token}/setWebhook", data=body, timeout=15) as response:
    if not json.load(response).get("ok"):
        sys.exit("Telegram refused the webhook")
PY
  echo "  Telegram delivers to the new tunnel"
fi

set -a
# shellcheck source=/dev/null
. ./.env
set +a
printf '\n✔ Agent Office is up\n'
printf '  API docs  http://localhost:%s/swagger-ui.html\n' "${AGENT_OFFICE_PORT:-8080}"
printf '  n8n       http://localhost:%s\n' "${N8N_PORT:-5678}"
if [[ "${TRELLO_ENABLED:-false}" == "true" ]]; then
  printf '  Trello    https://trello.com/b/%s\n' "${TRELLO_BOARD_ID}"
fi

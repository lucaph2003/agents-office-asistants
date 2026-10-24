#!/usr/bin/env bash
# Stops the Agent Office stack and its tunnel. Data is kept in the Docker volumes.
#
# Usage: scripts/stop.sh [--all]     --all also quits Ollama and OrbStack to free memory
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if docker info >/dev/null 2>&1; then
  docker compose stop
fi

if [[ -f .run/tunnel.pid ]]; then
  kill "$(cat .run/tunnel.pid)" 2>/dev/null && echo "Tunnel stopped"
  rm -f .run/tunnel.pid
fi

if [[ -f .run/office.pid ]]; then
  # npm starts Vite as a child process, so stop the whole group.
  pkill -TERM -P "$(cat .run/office.pid)" 2>/dev/null || true
  kill "$(cat .run/office.pid)" 2>/dev/null && echo "Office view stopped"
  rm -f .run/office.pid
fi

# SIGTERM to matching processes. pkill exits 1 when nothing matched, which is fine here
# (e.g. Ollama's server already exited with its app); other exit codes are real errors.
terminate() {
  pkill -TERM "$@" || [[ $? -eq 1 ]]
}

wait_until_gone() {  # wait_until_gone <pattern>
  for _ in $(seq 1 15); do
    pgrep -f "$1" >/dev/null || return 0
    sleep 1
  done
  return 1
}

if [[ "${1:-}" == "--all" ]]; then
  # The Ollama app refuses AppleScript "quit" requests, so ask it to terminate with SIGTERM.
  if pgrep -x Ollama >/dev/null || pgrep -f 'ollama serve' >/dev/null; then
    terminate -x Ollama
    terminate -f 'ollama serve'
    if wait_until_gone 'ollama serve'; then echo "Ollama closed"; else echo "✖ Ollama is still running" >&2; fi
  fi
  # This stops every container, not only Agent Office's.
  if command -v orbctl >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
    if orbctl stop >/dev/null 2>&1; then echo "OrbStack stopped"; else echo "✖ OrbStack did not stop" >&2; fi
  fi
fi

echo "✔ Agent Office is stopped. Cards added to the Trello Inbox meanwhile will be picked up on the next start."

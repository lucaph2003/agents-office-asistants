#!/usr/bin/env bash
# Creates the "Agent Office" board with its six lists (or reads an existing board) and prints
# the TRELLO_* lines to paste into .env.
#
# Usage:
#   TRELLO_API_KEY=... TRELLO_TOKEN=... scripts/trello-setup.sh            # create a new board
#   TRELLO_API_KEY=... TRELLO_TOKEN=... TRELLO_BOARD_ID=... scripts/trello-setup.sh   # reuse a board
#
# Get a key at https://trello.com/power-ups/admin and generate a token from the same page.
set -euo pipefail

: "${TRELLO_API_KEY:?Set TRELLO_API_KEY}"
: "${TRELLO_TOKEN:?Set TRELLO_TOKEN}"
BASE_URL="${TRELLO_BASE_URL:-https://api.trello.com}"
BOARD_NAME="${TRELLO_BOARD_NAME:-Agent Office}"

# Credentials go in a header so they never show up in URLs or shell history of the requests.
AUTH="Authorization: OAuth oauth_consumer_key=\"${TRELLO_API_KEY}\", oauth_token=\"${TRELLO_TOKEN}\""

trello() {
  local method=$1 path=$2 body=${3:-}
  if [[ -n "$body" ]]; then
    curl --silent --show-error --fail -X "$method" -H "$AUTH" -H 'Content-Type: application/json' \
      --data "$body" "${BASE_URL}${path}"
  else
    curl --silent --show-error --fail -X "$method" -H "$AUTH" "${BASE_URL}${path}"
  fi
}

json_field() {
  python3 -c "import json,sys; print(json.load(sys.stdin)['$1'])"
}

# Env variable, list name — in board order.
LISTS=(
  "TRELLO_LIST_INBOX|📥 Inbox"
  "TRELLO_LIST_PLANNED|📋 Planned"
  "TRELLO_LIST_IN_PROGRESS|🤖 In Progress"
  "TRELLO_LIST_REVIEW|👀 Review"
  "TRELLO_LIST_BLOCKED|⛔ Blocked"
  "TRELLO_LIST_DONE|✅ Done"
)

if [[ -z "${TRELLO_BOARD_ID:-}" ]]; then
  board=$(trello POST /1/boards/ "$(python3 -c 'import json,sys; print(json.dumps({"name": sys.argv[1], "defaultLists": False}))' "$BOARD_NAME")")
  TRELLO_BOARD_ID=$(echo "$board" | json_field id)
  echo "Created board '$BOARD_NAME': $(echo "$board" | json_field shortUrl)" >&2
  pos=1
  for entry in "${LISTS[@]}"; do
    name=${entry#*|}
    trello POST "/1/boards/${TRELLO_BOARD_ID}/lists" \
      "$(python3 -c 'import json,sys; print(json.dumps({"name": sys.argv[1], "pos": int(sys.argv[2])}))' "$name" "$pos")" > /dev/null
    pos=$((pos + 1))
  done
fi

existing=$(trello GET "/1/boards/${TRELLO_BOARD_ID}/lists?fields=name")

echo "TRELLO_ENABLED=true"
echo "TRELLO_BOARD_ID=${TRELLO_BOARD_ID}"
for entry in "${LISTS[@]}"; do
  var=${entry%%|*}
  name=${entry#*|}
  id=$(echo "$existing" | python3 -c '
import json, sys
wanted = sys.argv[1]
plain = wanted.split(" ", 1)[1].lower()
for lst in json.load(sys.stdin):
    if lst["name"] == wanted or lst["name"].lower().endswith(plain):
        print(lst["id"]); break
' "$name")
  if [[ -z "$id" ]]; then
    echo "List '$name' not found on board ${TRELLO_BOARD_ID}" >&2
    exit 1
  fi
  echo "${var}=${id}"
done

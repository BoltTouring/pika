#!/usr/bin/env bash
set -euo pipefail

my_pubkey=""

trim() {
  local s="$1"
  s="${s#"${s%%[![:space:]]*}"}"
  s="${s%"${s##*[![:space:]]}"}"
  printf '%s' "$s"
}

while IFS= read -r line; do
  [[ -z "${line}" ]] && continue

  msg_type="$(printf '%s' "$line" | jq -r '.type // empty' 2>/dev/null || true)"
  case "$msg_type" in
    ready)
      my_pubkey="$(printf '%s' "$line" | jq -r '.pubkey // ""' 2>/dev/null || true)"
      printf '{"cmd":"publish_keypackage"}\n'
      ;;
    message_received)
      from_pubkey="$(printf '%s' "$line" | jq -r '.from_pubkey // ""' 2>/dev/null || true)"
      if [[ -n "$my_pubkey" && "$from_pubkey" == "$my_pubkey" ]]; then
        continue
      fi

      group_id="$(printf '%s' "$line" | jq -r '.nostr_group_id // ""' 2>/dev/null || true)"
      content="$(printf '%s' "$line" | jq -r '.content // ""' 2>/dev/null || true)"

      response=""
      if [[ "$content" =~ [Rr]eply[[:space:]]+with[[:space:]]+exactly:[[:space:]]*(.+)$ ]]; then
        response="$(trim "${BASH_REMATCH[1]}")"
      fi
      if [[ -z "$response" ]]; then
        response="ack: $content"
      fi

      jq -cn \
        --arg gid "$group_id" \
        --arg content "$response" \
        '{"cmd":"send_message","nostr_group_id":$gid,"content":$content}'
      ;;
    *)
      ;;
  esac
done

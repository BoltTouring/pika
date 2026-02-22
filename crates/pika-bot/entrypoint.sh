#!/usr/bin/env bash
set -euo pipefail

STATE_DIR="${STATE_DIR:-/app/state}"
mkdir -p "$STATE_DIR"

if [ -n "${NOSTR_SECRET_KEY:-}" ]; then
  cat > "$STATE_DIR/identity.json" <<IDENTITY
{"secret_key_hex":"$NOSTR_SECRET_KEY","public_key_hex":""}
IDENTITY
fi

tmp_dir="$(mktemp -d)"
marmot_in="$tmp_dir/marmot-in.fifo"
marmot_out="$tmp_dir/marmot-out.fifo"
mkfifo "$marmot_in" "$marmot_out"

cleanup() {
  rm -rf "$tmp_dir"
}
trap cleanup EXIT

# Open both FIFOs read-write in the parent to avoid blocking opens.
exec 3<>"$marmot_in"
exec 4<>"$marmot_out"

python3 /app/pi-bridge.py < "$marmot_out" > "$marmot_in" &
bridge_pid=$!

/app/marmotd daemon \
  --relay wss://us-east.nostr.pikachat.org \
  --relay wss://eu.nostr.pikachat.org \
  --state-dir "$STATE_DIR" \
  < "$marmot_in" > "$marmot_out" &
marmot_pid=$!

wait "$marmot_pid"
status=$?
kill "$bridge_pid" 2>/dev/null || true
wait "$bridge_pid" 2>/dev/null || true
exit "$status"

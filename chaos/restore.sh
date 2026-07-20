#!/usr/bin/env bash
# Restore the link to clean: remove every toxic the other chaos scripts can add.
#
# Useful after satellite.sh, or when flap.sh was interrupted mid-cycle and left the
# link stuck DOWN. Removing an absent toxic is not an error — this is safe to run
# at any time, including twice.
#
# Prereq: same Toxiproxy setup as the other scripts (see chaos/README.md).
set -euo pipefail

PROXY="${PROXY:-castaway-cloud}"
CLI="${TOXIPROXY_CLI:-toxiproxy-cli}"

if ! command -v "$CLI" >/dev/null 2>&1; then
  echo "[restore] error: '$CLI' not found — install it with 'brew install toxiproxy'," >&2
  echo "[restore]        or set TOXIPROXY_CLI to its path." >&2
  exit 1
fi

# Every toxic name used by the harness: partition.sh, satellite.sh, flap.sh.
TOXICS=(partition sat-latency sat-bandwidth sat-loss flap)

echo "[restore] removing toxics from '$PROXY'..."
for toxic in "${TOXICS[@]}"; do
  if "$CLI" toxic remove "$PROXY" -n "$toxic" >/dev/null 2>&1; then
    echo "[restore]   removed $toxic"
  fi
done

echo "[restore] done — '$PROXY' is clean. castaway should converge back to ONLINE."
echo "[restore] remaining toxics (should be none):"
"$CLI" inspect "$PROXY" 2>/dev/null || true

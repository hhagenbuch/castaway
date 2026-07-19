#!/usr/bin/env bash
# Flapping link: oscillate up/down to exercise the LinkMonitor's hysteresis. If the
# monitor is tuned right, the reported state should NOT thrash on every blip.
#
# Prereq: same Toxiproxy setup as satellite.sh.
set -euo pipefail

PROXY="${PROXY:-castaway-cloud}"
CLI="${TOXIPROXY_CLI:-toxiproxy-cli}"
CYCLES="${CYCLES:-6}"
PERIOD="${PERIOD:-3}"

echo "[flap] oscillating '$PROXY' $CYCLES times, ${PERIOD}s each half-cycle..."
for i in $(seq 1 "$CYCLES"); do
  "$CLI" toxic add "$PROXY" -t timeout -a timeout=1 -n flap 2>/dev/null || true
  echo "[flap] cycle $i: DOWN"
  sleep "$PERIOD"
  "$CLI" toxic remove "$PROXY" -n flap 2>/dev/null || true
  echo "[flap] cycle $i: UP"
  sleep "$PERIOD"
done
echo "[flap] done. Watch GET /api/link/stream — transitions should be damped, not 1:1 with blips."

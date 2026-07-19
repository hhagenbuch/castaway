#!/usr/bin/env bash
# Satellite-link profile: high latency, packet loss, constrained bandwidth. This is
# the DEGRADED state the whole project is about — reachable, but expensive to use.
#
# Prereq: `brew install toxiproxy`, and castaway started with
#   --castaway.cloud.base-url=http://localhost:8666
# pointing at a Toxiproxy proxy named "$PROXY" whose upstream is the cloud API.
set -euo pipefail

PROXY="${PROXY:-castaway-cloud}"
CLI="${TOXIPROXY_CLI:-toxiproxy-cli}"

echo "[satellite] applying 700ms latency + 5% loss + 200kbps to '$PROXY'..."
"$CLI" toxic add "$PROXY" -t latency   -a latency=700 -a jitter=100 -n sat-latency   2>/dev/null || true
"$CLI" toxic add "$PROXY" -t bandwidth -a rate=200                  -n sat-bandwidth 2>/dev/null || true
# Toxiproxy models loss as timed-out slices; ~5% via a small timeout toxic on a fraction.
"$CLI" toxic add "$PROXY" -t timeout   -a timeout=2000 --toxicity 0.05 -n sat-loss  2>/dev/null || true
echo "[satellite] done. Expect castaway to report DEGRADED and prefer the local model."
echo "[satellite] restore with: chaos/restore.sh (or remove sat-* toxics)"

#!/usr/bin/env bash
# Hard network partition: cut castaway off from the cloud entirely.
#
# Two ways to run it:
#   1. Zero-dependency (recommended for the demo): flip the runtime into a forced
#      OFFLINE state — no proxy needed. Start castaway with:
#        mvn spring-boot:run -Dspring-boot.run.arguments=--castaway.link.forced-state=OFFLINE
#      or hot-toggle nothing and just pull Wi-Fi; the LinkMonitor detects it.
#   2. Realistic (Toxiproxy): sever the proxy castaway routes cloud traffic through.
#
# This script does option 2. Prereq: `brew install toxiproxy` and castaway started
# with --castaway.cloud.base-url=http://localhost:8666 (the proxy listen address).
set -euo pipefail

PROXY="${PROXY:-castaway-cloud}"
CLI="${TOXIPROXY_CLI:-toxiproxy-cli}"

echo "[partition] cutting cloud link via Toxiproxy proxy '$PROXY'..."
"$CLI" toxic add "$PROXY" -t timeout -a timeout=1 -n partition 2>/dev/null \
  || "$CLI" toxic update "$PROXY" -n partition -a timeout=1
echo "[partition] done. castaway should converge to OFFLINE within a probe cycle."
echo "[partition] restore with: $CLI toxic remove $PROXY -n partition"

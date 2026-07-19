#!/usr/bin/env bash
# Evals under partition: run the SAME golden suite against castaway ONLINE and then
# OFFLINE, with different thresholds and honesty assertions. Running your own eval
# harness against your own runtime under a simulated partition is the point of the
# whole project.
#
# Prereqs:
#   - Ollama running with a chat model pulled (ollama pull qwen3:8b) for the OFFLINE run
#   - ANTHROPIC_API_KEY exported (cloud model for ONLINE + the LLM judge)
#   - agent-evals built: set EVALS_JAR to its shaded jar
#       https://github.com/hhagenbuch/agent-evals
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
EVALS_JAR="${EVALS_JAR:?set EVALS_JAR to the agent-evals shaded jar}"
PORT="${PORT:-8080}"

run_profile() {
  local name="$1" forced="$2" dataset="$3" report="$4"
  echo "=== profile: $name (forced-state=$forced) ==="
  ( cd "$ROOT" && mvn -q spring-boot:run \
      -Dspring-boot.run.arguments="--castaway.link.forced-state=$forced --server.port=$PORT" ) &
  local pid=$!
  # wait for readiness
  until curl -sf "localhost:$PORT/actuator/health" >/dev/null 2>&1; do sleep 1; done
  curl -s "localhost:$PORT/api/link"    # show the forced state
  java -jar "$EVALS_JAR" "$dataset" --target "http://localhost:$PORT/api/chat" --report "$report" || true
  kill "$pid" 2>/dev/null || true
  wait "$pid" 2>/dev/null || true
}

run_profile online  ONLINE  "$HERE/castaway-online.yaml"  "$HERE/report-online.md"
run_profile offline OFFLINE "$HERE/castaway-offline.yaml" "$HERE/report-offline.md"

echo "Reports: evals/report-online.md, evals/report-offline.md"

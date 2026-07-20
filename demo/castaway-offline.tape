# castaway offline demo — reproducible terminal recording.
#
#   brew install vhs && vhs demo/castaway-offline.tape        (run from the repo root)
#
# Prerequisites (the KEYLESS path — no ANTHROPIC_API_KEY needed):
#   - ollama serve running, with `ollama pull qwen3:8b`
#   - mvn -q package -DskipTests   (builds target/castaway-*.jar)
#
# The scene: the link is forced OFFLINE, so every answer comes from the local
# model, and a side-effecting tool queues instead of firing.
#
# Note on what the recording does and does not show: the outbox ends with one
# entry. How many times the model actually called send_email varies run to run
# (small models are eager) — the content-derived idempotency key is what makes
# the queued count one regardless. A given recording may show a single call, so
# don't read "one entry" as proof of de-duplication; that is covered by the
# outbox tests, not by this GIF.

Output docs/castaway-offline-demo.gif

Set Shell zsh
Set FontSize 15
Set Width 1180
Set Height 620
Set Framerate 10
Set TypingSpeed 12ms
Set Theme "Catppuccin Mocha"
Set Padding 14
Set Margin 8
Set WindowBar Colorful

# ---------------------------------------------------------------- setup (hidden)
# Frames are not captured while hidden, so app startup and model warm-up cost
# wall-clock but add nothing to the GIF's length or file size.
Hide
Type `rm -f /tmp/castaway-demo.db /tmp/castaway-demo.log` Enter
Sleep 1s
Type `java -jar target/castaway-0.1.0-SNAPSHOT.jar --castaway.link.forced-state=OFFLINE --castaway.local.model=qwen3:8b --castaway.outbox.path=/tmp/castaway-demo.db > /tmp/castaway-demo.log 2>&1 &` Enter
Sleep 2s
Type `until curl -sf localhost:8080/api/link >/dev/null 2>&1; do sleep 1; done` Enter
Sleep 45s
# Warm the model so the recording shows steady-state latency, not first-load.
Type `curl -s localhost:8080/api/chat -H 'content-type: application/json' -d '{"message":"hi"}' >/dev/null` Enter
Sleep 35s
Type `clear` Enter
Sleep 1s
Show

# ---------------------------------------------------------------- the demo
# 1. The link is down. Not degraded, not pretending — down.
Type `curl -s localhost:8080/api/link` Enter
Sleep 3s

# 2. Ask a question anyway. It answers — from the local model, and says so.
Type `curl -s localhost:8080/api/chat -H 'content-type: application/json' \` Enter
Type `  -d '{"message":"In one sentence, what is a satellite link?"}' | jq '{reply, provenance}'` Enter
Sleep 9s

# 3. Ask for a real side effect. Offline, it drafts and QUEUES — it does not
#    claim to have sent the mail.
Type `curl -s localhost:8080/api/chat -H 'content-type: application/json' \` Enter
Type `  -d '{"message":"Email bob@example.com to move our 2pm meeting to 3pm."}' | jq '{reply, toolsUsed}'` Enter
Sleep 15s

# 4. The payoff: one queued action, holding whether the model called send_email
#    once or three times — the idempotency key is derived from the content.
Type `curl -s localhost:8080/api/outbox | jq '[.[] | {id, actionType, state}]'` Enter
Sleep 5s

# ---------------------------------------------------------------- teardown (hidden)
Hide
Type `pkill -f castaway-0.1.0-SNAPSHOT.jar; rm -f /tmp/castaway-demo.db /tmp/castaway-demo.log` Enter
Sleep 2s

# castaway offline demo — reproducible terminal recording.
#
#   brew install vhs && vhs demo/castaway-offline.tape        (run from the repo root)
#
# Prerequisites (the KEYLESS path — no ANTHROPIC_API_KEY needed):
#   - ollama serve running, with `ollama pull qwen3:8b`
#   - mvn -q package -DskipTests   (builds target/castaway-*.jar)
#
# The scene: the link is forced OFFLINE, so every answer comes from the local
# model, a side-effecting tool queues instead of firing, and the same action
# requested twice still queues exactly once.
#
# On the de-duplication beat: the outbox key is derived from the *content* of the
# action, so the request dictates subject and body. That is deliberate — asked
# vaguely ("email Bob to move the meeting"), a small model re-words the subject
# on each turn, which is a genuinely different email and correctly queues again.
# Same action in, one entry out; different action in, two entries out.

Output docs/castaway-offline-demo.gif

Set Shell zsh
Set FontSize 15
Set Width 1180
Set Height 680
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
# Title card: painted while hidden so the GIF opens on it, not on a bare prompt.
Type `clear && printf '\n  \033[1;36mcastaway\033[0m — agent runtime for disconnected environments\n\n  \033[2mno API key · local model: qwen3:8b · link forced OFFLINE\033[0m\n\n'` Enter
Sleep 2s
Show
Sleep 2500ms

# ---------------------------------------------------------------- the demo
# 1. The link is down. Not degraded, not pretending — down.
Type `curl -s localhost:8080/api/link` Enter
Sleep 2500ms

# 2. Ask a question anyway. It answers — from the local model, and says so.
Type `curl -s localhost:8080/api/chat -H 'content-type: application/json' \` Enter
Type `  -d '{"message":"In one sentence, what is a satellite link?"}' | jq '{reply, provenance}'` Enter
Sleep 8s

# 3. Ask for a real side effect. Offline, it drafts and QUEUES — it does not
#    claim to have sent the mail.
Type `E='{"message":"Email bob@example.com with subject \"Reschedule\" and body \"Move our 2pm to 3pm.\""}'` Enter
Sleep 1s
Type `curl -s localhost:8080/api/chat -H 'content-type: application/json' -d "$E" | jq '{reply, toolsUsed}'` Enter
Sleep 14s

# 4. Ask for the exact same action again. Same content -> same idempotency key.
#    Only toolsUsed is shown here, deliberately: a small model sometimes invents
#    an id in its prose ("queued as ID 2") when the outbox holds one entry. The
#    narration is not the source of truth, so the recording does not quote it.
Type `curl -s localhost:8080/api/chat -H 'content-type: application/json' -d "$E" | jq '{toolsUsed}'` Enter
Sleep 18s

# 5. The payoff: two requests, and the model often calls send_email more than
#    once per turn — still ONE queued action.
Type `curl -s localhost:8080/api/outbox | jq '[.[] | {id, state, key: .idempotencyKey[0:8]}]'` Enter
Sleep 5s

# ---------------------------------------------------------------- teardown (hidden)
Hide
Type `pkill -f castaway-0.1.0-SNAPSHOT.jar; rm -f /tmp/castaway-demo.db /tmp/castaway-demo.log` Enter
Sleep 2s

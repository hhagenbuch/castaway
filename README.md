# castaway

> Every agent framework assumes the API is always there. The real world has
> ships on satellite links, mines, aircraft, rural clinics, and factory floors
> where connectivity is intermittent, expensive, and slow. `castaway` is an
> agent runtime built for that world: it degrades explicitly instead of
> failing, routes to a local model when the link drops, queues side-effects
> for reconciliation, and syncs memory when the connection returns.

**Status: Phase 3 — event-log memory sync, chaos harness, evals under partition.**
On top of Phase 1 routing and the Phase 2 capability gate + revalidating outbox,
conversation memory is now an append-only event log whose ship↔shore sync is log
shipping with last-writer-wins conflict resolution and fold-back; network
conditions are reproducible fixtures; and the same golden suite runs ONLINE and
OFFLINE with honesty assertions. See [`docs/DESIGN.md`](docs/DESIGN.md) for the
full RFC; the roadmap below tracks progress.

## Architecture

```
                       ┌─────────────────────────────────────────┐
                       │              castaway runtime            │
User ──► Chat API ──►  │  AgentLoop (bounded reactive tool loop)  │
                       │      │                                   │
                       │  ModelRouter ──► CloudLlmClient (Anthropic)
                       │      │       └─► LocalLlmClient (Ollama)  │
                       │      ▲                                    │
                       │  LinkMonitor (ONLINE/DEGRADED/OFFLINE)    │
                       │      │                                    │
                       │  CapabilityGate ──► ToolRegistry          │
                       │      │        (tools declare link needs)  │
                       │  Outbox ──► reconciler (on reconnect)     │
                       │  MemoryLog ──► sync (on reconnect)        │
                       └─────────────────────────────────────────┘
```

Six components, each a distributed-systems problem applied to a new domain:
**LinkMonitor** (link state machine with hysteresis), **ModelRouter** (cloud
vs. local per request, provenance-tagged), **CapabilityGate** (tools declare
their connectivity needs; the agent negotiates scope when degraded), **Outbox**
(durable queue of deferrable side-effects with semantic revalidation on
reconnect), **MemoryLog** (append-only conversation log; sync is log shipping),
and a **Chaos harness** (Toxiproxy-driven network fixtures). Full detail in
[`docs/DESIGN.md`](docs/DESIGN.md).

## The demo

Pull the plug mid-conversation and the agent keeps going: routes to the local
model, shows an `OFFLINE` banner, drafts and *queues* an email instead of
pretending it sent it, then reconciles when the link returns — revalidating
each queued action against current state before it fires.

## Running it (Phase 1)

```bash
# 1. A local fallback model, served by Ollama:
ollama pull qwen3:8b

# 2. The cloud model (optional — without a key, castaway simply lives offline):
export ANTHROPIC_API_KEY=sk-ant-...

mvn spring-boot:run
```

Watch the link state, and stream its transitions:

```bash
curl -s localhost:8080/api/link           # {"link":"ONLINE"}
curl -N localhost:8080/api/link/stream     # SSE feed of ONLINE/DEGRADED/OFFLINE
```

Chat — the reply is tagged with where it came from:

```bash
curl -s localhost:8080/api/chat -H 'content-type: application/json' \
  -d '{"message":"What is 21 * 2?"}'
# online:   {"reply":"42", ..., "provenance":"cloud:claude-sonnet-5 (ONLINE)"}
# wifi off: {"reply":"42", ..., "provenance":"local:qwen3:8b (OFFLINE)"}
```

Turn Wi-Fi off, wait for `OFFLINE`, and the same endpoint keeps answering from
the local model — honestly labelled, never pretending it was the cloud. The
`LinkMonitor` uses hysteresis (N consecutive agreeing probes) so a single
dropped packet can't flap the state.

**Pull the plug mid-request** and you don't even wait for the state to flip: the
monitor detects a dead link but doesn't protect against one, so `ModelRouter`
also catches a cloud connectivity failure in-band, retries on the local model,
and tags the answer `local:qwen3:8b (FALLBACK)` — then nudges the monitor toward
`OFFLINE`. The conversation survives the plug being pulled, not just a link
that's already known-down.

### Deferred actions with revalidation (Phase 2)

Offline, tools that have real side-effects don't fire — they queue. Ask the
agent to send an email while `OFFLINE`:

```bash
curl -s localhost:8080/api/chat -H 'content-type: application/json' \
  -d '{"message":"Email bob@example.com to move our 2pm meeting to 3pm."}'
# reply: "...I've QUEUED the email to bob@example.com (id 1); it will be
#         revalidated and sent automatically when the connection returns..."

curl -s localhost:8080/api/outbox     # [{ "state":"QUEUED", "actionType":"send_email", ... }]
```

The agent knows it's offline (the CapabilityGate tells it), so it drafts and
queues rather than claiming it sent the mail. When the link returns, the
reconciler **revalidates each queued action against the current state** before
executing it — an email queued to reschedule a meeting that has since passed is
marked `STALE` and surfaced, never sent. Online-only tools (e.g. `live_price`)
are hidden entirely while offline, so the model can't invent a live answer.

The outbox is embedded SQLite, so queued actions survive a restart. Its lifecycle
is `QUEUED → REVALIDATED → EXECUTED | STALE` (see [`docs/DESIGN.md`](docs/DESIGN.md) §3).

### Memory as an event log, and resilience testing (Phase 3)

Conversation memory is an **append-only event log** (`MemoryLog`), so syncing two
instances (a ship offline, a shore online talking to the same session) is log
shipping by high-water mark, not diffing. When both append while partitioned, the
divergence is detected, both branches are kept, the visible head is resolved
last-writer-wins, and a `FOLD_BACK` event carries the losing branch back as context
("while you were offline, you also asked X elsewhere") — nothing is silently dropped.

Two ways to make the network misbehave on purpose:

- **[`chaos/`](chaos/)** — Toxiproxy scenarios (`partition`, `satellite`, `flap`) that
  reproduce a hard cut, a 700 ms/5%-loss satellite link, and a flapping link.
- **[`evals/`](evals/)** — the same golden suite run `ONLINE` and `OFFLINE` (via the
  `--castaway.link.forced-state` hook) with **honesty assertions**: offline, the agent
  must acknowledge it's offline and must not claim the email was sent. Degradation
  measured as physics, not vibes.

## Roadmap

- [x] Phase 0 — design doc ([`docs/DESIGN.md`](docs/DESIGN.md))
- [x] Phase 1 — skeleton + `LinkMonitor` + `ModelRouter` (local model via Ollama)
- [x] Phase 2 — `CapabilityGate` + SQLite `Outbox` + reconciler with revalidation
- [x] Phase 3 — append-only `MemoryLog` + sync/merge/fold-back, [chaos harness](chaos/),
      [evals under partition](evals/) (two-instance *networked* ship/shore demo deferred)
- [ ] Phase 4 — demo GIF, local-model benchmark table, README polish
- [ ] Later — multi-node memory mesh; k8s operator integration ([agent-operator](https://github.com/hhagenbuch/agent-operator))

## Related

- [spring-ai-agent-starter](https://github.com/hhagenbuch/spring-ai-agent-starter) — the production-JVM agent core castaway builds on
- [agent-evals](https://github.com/hhagenbuch/agent-evals) — the eval harness used for evals-under-partition

## License

MIT — see [LICENSE](LICENSE).

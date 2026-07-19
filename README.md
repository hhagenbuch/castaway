# castaway

> Every agent framework assumes the API is always there. The real world has
> ships on satellite links, mines, aircraft, rural clinics, and factory floors
> where connectivity is intermittent, expensive, and slow. `castaway` is an
> agent runtime built for that world: it degrades explicitly instead of
> failing, routes to a local model when the link drops, queues side-effects
> for reconciliation, and syncs memory when the connection returns.

**Status: Phase 0 — design.** This repo currently contains the design only.
See [`docs/DESIGN.md`](docs/DESIGN.md) for the RFC. Code lands in phases; the
roadmap below tracks progress.

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

## Roadmap

- [ ] Phase 0 — design doc (this)
- [ ] Phase 1 — skeleton + `LinkMonitor` + `ModelRouter` (local model via Ollama)
- [ ] Phase 2 — `CapabilityGate` + `Outbox` + reconciler with revalidation
- [ ] Phase 3 — `MemoryLog` sync + chaos harness + evals-under-partition
- [ ] Phase 4 — demo GIF, local-model benchmark table, README polish
- [ ] Later — multi-node memory mesh; k8s operator integration ([agent-operator](https://github.com/hhagenbuch/agent-operator))

## Related

- [spring-ai-agent-starter](https://github.com/hhagenbuch/spring-ai-agent-starter) — the production-JVM agent core castaway builds on
- [agent-evals](https://github.com/hhagenbuch/agent-evals) — the eval harness used for evals-under-partition

## License

MIT — see [LICENSE](LICENSE).

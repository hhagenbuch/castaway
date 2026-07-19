# castaway — Design (RFC)

**Status:** Draft / pre-code (Phase 0)
**Author:** Heyward Hagenbuch

An agent runtime for disconnected and degraded environments. This document is
the contract for what gets built; code follows it in phases.

## 1. Problem

Agent frameworks assume a reliable, cheap, low-latency path to a hosted model
and to the tools the agent calls. That assumption fails wherever connectivity
is intermittent, expensive, or slow: vessels on satellite links, mines,
aircraft, rural clinics, factory floors. In those environments the usual
failure mode is the worst one — the agent hangs on a timeout, or worse, it
hallucinates that a side-effect succeeded.

castaway treats connectivity as a first-class runtime condition. It **degrades
explicitly**: it tells the user it is offline, routes to a local model, refuses
to *claim* an action executed when it merely queued it, and reconciles when the
link returns. Honesty under degradation is the core design principle — every
other decision serves it.

Non-framing note: ships are *one example among several*. The runtime is
domain-neutral.

## 2. Components

### 2.1 LinkMonitor
A state machine over three states — `ONLINE`, `DEGRADED`, `OFFLINE` — fed by
active probes to the cloud endpoint. Each probe measures **reachability and
round-trip time**; classification is `OFFLINE` when unreachable, `DEGRADED` when
reachable but above an RTT threshold, else `ONLINE`. Hysteresis (require N
consecutive agreeing probes) prevents flapping. It emits transitions as events;
every other component subscribes.

`DEGRADED` is the interesting state and the reason three states beat two:
satellite links are rarely *down*, they are ~700 ms RTT (and lossy). In
`DEGRADED` the router prefers the local model for long generations (latency ×
tokens is brutal over such a link) but may still reach for the cloud on a hard
reasoning step.

> **Probe fidelity.** The Phase 1 probe drives `DEGRADED` off RTT and timeouts.
> Packet-loss sampling (send N, measure delivered) is a planned refinement; the
> lossy-link scenario is exercised end-to-end by the Phase 3 chaos harness
> (`satellite.sh`: 700 ms + 5% loss). The monitor is also self-healing — it must
> survive the very conditions it measures, so the probe stream drops surplus
> ticks and resubscribes on error rather than freezing at a stale state.

Exposed at `GET /api/link` and as an SSE stream (`GET /api/link/stream`) so a
dashboard/demo can render the banner live.

### 2.2 ModelRouter
Implements the starter's existing `LlmClient` interface — this is the payoff of
that abstraction — and delegates per request based on link state:

| Link       | Routing                                                        |
|------------|----------------------------------------------------------------|
| `ONLINE`   | Cloud (Anthropic).                                             |
| `DEGRADED` | Policy choice against a cost/latency budget (see §5).          |
| `OFFLINE`  | Local model via Ollama (`LocalLlmClient`).                     |

Every response carries **provenance** — e.g. `answered-by: local-qwen3-8b,
link: OFFLINE` — surfaced to the user, not hidden in a log. `CloudLlmClient`
and `LocalLlmClient` both normalize to the internal `LlmResponse`.

### 2.3 CapabilityGate
Tools declare a link requirement via a `LinkRequirement` enum on `AgentTool`:

- `OFFLINE_CAPABLE` — pure/local (calculator, local search). Always available.
- `DEFERRABLE` — has a side-effect that can be queued (send email). Available
  offline in *draft* mode: the model may compose it but not execute it.
- `ONLINE_ONLY` — needs live data (live pricing). Hidden when not `ONLINE`.

Offline, the gate rewrites the toolset the model sees **and** injects a
system-prompt notice: *"You are offline. You may draft but not execute
deferrable actions, and must say so. These tools are unavailable: …"* The
agent's knowledge of its own degradation is the novel UX — it negotiates scope
with the user instead of failing or lying.

### 2.4 Outbox
Deferrable side-effects go to a durable queue (SQLite via JDBC — embedded, no
server, survives restart). Each entry stores an **idempotency key**, the
conversational context that produced it, the proposed action, and a **TTL**.

On reconnect the reconciler does **not** blindly replay. It first
**revalidates**: a cloud-model check of the proposed action against current
state before execution. An action queued six hours ago may be stale — "the
meeting it was rescheduling has already passed." Stale actions surface to the
user instead of firing. **This revalidation step is the most original idea in
the project and is not optional.**

### 2.5 MemoryLog
Conversation memory is an **append-only event log**, not mutable state. That
makes ship↔shore sync log shipping + merge rather than diffing. When the same
session diverges (the user talked to a shore-side agent while the ship was
offline), resolution is **last-writer-wins per session with both branches
retained**; a cloud-model summarization pass folds the divergent branch back in
as context: *"While you were offline you also asked X elsewhere."* See §4 for
the consistency model.

### 2.6 Chaos harness
Network conditions are **test fixtures, not accidents**. Toxiproxy sits between
castaway and the cloud API, driven by scenario scripts under `chaos/`:
`partition.sh` (hard cut), `satellite.sh` (700 ms + 5% loss + 200 kbps),
`flap.sh` (oscillate to exercise hysteresis).

## 3. Offline-action state machine

```
PROPOSED ──► QUEUED ──► REVALIDATED ──► EXECUTED
   │            │             │
   │            │             └──► STALE     (revalidation says the action no
   │            │                            longer makes sense — surfaced to user)
   │            └──────────────────► (expires via TTL → STALE)
   └──► REJECTED  (user or gate declines at draft time)
```

- **PROPOSED** — model drafted a deferrable action while offline.
- **QUEUED** — persisted to the Outbox with idempotency key + context + TTL.
- **REVALIDATED** — on reconnect, cloud model checked action vs. current state.
- **EXECUTED** — revalidation passed; side-effect performed exactly once
  (idempotency key guards double-fire).
- **STALE** — revalidation failed or TTL expired; surfaced, never fired.
- **REJECTED** — declined at draft time.

## 4. Consistency model (MemoryLog)

- **Unit:** an append-only, per-session log of typed events (user message,
  assistant message, tool call, tool result, action proposal/outcome). Events
  are immutable and ordered by a per-session Lamport-ish counter + wall clock.
- **Sync:** on reconnect, each side ships the events the other lacks (by
  `(sessionId, seq)` high-water mark). Merge is a union; identical events
  dedupe by content hash.
- **Divergence:** if both sides appended to the same session while partitioned,
  both branches are kept. A resolution event records LWW *for the visible head*
  (deterministic by `(wallClock, nodeId)`), and a summarization event folds the
  losing branch in as context so nothing is silently dropped.
- **Guarantee:** no lost writes (both branches retained), eventual convergence
  of the visible head, and the model is always *told* when a fold-back happened.

## 5. Local-model reality check (designed in, not discovered late)

Small local models are materially worse at tool calling. Mitigations, in order:

1. Constrain offline tool calls with strict JSON schemas + retry-on-invalid.
2. Shrink the offline toolset to 2–3 tools (CapabilityGate already does this).
3. Pick a model with decent function-calling at 4–8B empirically — evaluate
   Qwen3 and Llama 3.x-class models via Ollama with the `agent-evals` suite and
   **record the comparison as a benchmark table in the README.**
4. If offline tool calling stays unreliable: fall back to *offline = Q&A +
   drafting only, no tool execution.* An honest, defensible design decision,
   not a failure.

The `DEGRADED` policy (§2.2) trades against a cost/latency budget: prefer local
for long generations, allow cloud for short high-value reasoning, cap cloud
spend/latency per request.

## 6. Reuse vs. self-containment

castaway needs the starter's `AgentLoop`, `LlmClient`/`LlmResponse`,
`ToolRegistry`, `AgentTool`, and `ConversationMemory` abstractions.

**Decision:** *copy* the ~6 core classes into castaway with attribution rather
than depend on the starter via JitPack. Rationale: castaway is a runtime meant
to run in hostile environments; a self-contained build with no external
snapshot dependency is worth more than DRY here. The copied files carry a header
pointing back to `spring-ai-agent-starter`.

## 7. Non-goals (MVP)

- No multi-agent orchestration.
- No Kubernetes / operator integration yet (that is [agent-operator](https://github.com/hhagenbuch/agent-operator)).
- No fine-tuning or model training.
- No traffic-split / multi-node beyond the two-instance sync demo.

## 8. Stack

Spring Boot 3.3, Java 21, WebFlux (same as the starter). SQLite (JDBC) for the
Outbox. Ollama for the local model. Toxiproxy for the chaos harness.
`agent-evals` for evals-under-partition.

## 9. Build phases

0. **Design** — this document, committed alone.
1. **Skeleton + LinkMonitor + ModelRouter** — Wi-Fi off, chat still answers via
   Ollama, response tagged `link: OFFLINE`.
2. **CapabilityGate + Outbox + reconciler** — the queue → revalidate →
   execute/stale flow, with `SendEmailTool` (fake transport) as the canonical
   deferrable action.
3. **MemoryLog sync + chaos harness + evals-under-partition** — two-instance
   ship/shore sync; the same golden cases run ONLINE and OFFLINE with different
   thresholds and honesty assertions ("acknowledges being offline / does not
   claim the email was sent").
4. **Demo + README** — recorded GIF, benchmark table, design-decisions section.

If time-boxed, cut Phase 3's two-instance sync **before** cutting revalidation,
honesty-under-degradation, or evals-under-partition — those are the
differentiators.

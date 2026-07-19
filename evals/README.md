# Evals under partition

The same golden cases, run against castaway **ONLINE** and **OFFLINE**, with
different expectations per network profile — driven by
[agent-evals](https://github.com/hhagenbuch/agent-evals). Offline answers are
allowed to be weaker, but they must never *lie*: never claim an online-only
result, never claim a queued action was performed.

| Dataset | Link | What it asserts |
|---------|------|-----------------|
| `castaway-online.yaml` | `ONLINE` | math is grounded via the calculator; email actually sends; live price is returned. |
| `castaway-offline.yaml` | `OFFLINE` | math still works (calculator is offline-capable); the email is **queued, not sent** (`not_contains` + a judge honesty check); the online-only price tool is declined, not hallucinated. |

The partition is deterministic via the runtime hook `--castaway.link.forced-state`
(no Toxiproxy needed): the OFFLINE run pins the link OFFLINE so every request
exercises the local model + capability gating.

## Run it

```bash
ollama pull qwen3:8b
export ANTHROPIC_API_KEY=sk-ant-...          # cloud model + the LLM judge
export EVALS_JAR=/path/to/agent-evals-*.jar  # built from hhagenbuch/agent-evals
evals/run-partition-evals.sh
# -> evals/report-online.md, evals/report-offline.md
```

## Why this is the slide

The honesty assertions are the whole thesis made executable: an agent that
*knows* it's degraded and says so scores well offline; one that pretends the
email was sent fails the `not_contains`/judge checks. It's degradation as
physics, not vibes — measured, on every change.

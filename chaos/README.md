# Chaos harness

Network conditions as **test fixtures, not accidents**. These scripts drive
[Toxiproxy](https://github.com/Shopify/toxiproxy) sitting between castaway and the
cloud API, so the ONLINE / DEGRADED / OFFLINE behaviours are reproducible.

| Script | Scenario |
|--------|----------|
| `partition.sh` | Hard cut — cloud unreachable → castaway converges to `OFFLINE`. |
| `satellite.sh` | 700 ms latency + ~5% loss + 200 kbps → `DEGRADED`, router prefers local. |
| `flap.sh` | Oscillate up/down → exercises the monitor's hysteresis (state should not thrash). |

## Setup

```bash
brew install toxiproxy
toxiproxy-server &                                   # control plane on :8474
# Point a proxy's upstream at the cloud API, listening on :8666
toxiproxy-cli create castaway-cloud -l localhost:8666 -u api.anthropic.com:443
# Start castaway routing cloud traffic through the proxy:
mvn spring-boot:run -Dspring-boot.run.arguments=--castaway.cloud.base-url=http://localhost:8666
```

Then run a scenario (e.g. `chaos/satellite.sh`) and watch `GET /api/link/stream`.

## The honest caveat

The cloud API is HTTPS, and a plain TCP proxy in front of a TLS upstream needs SNI
handling a bare Toxiproxy listener doesn't provide — so for a faithful end-to-end
run, point the proxy at a **plaintext echo/mock upstream** (or a local stub of the
Messages API) rather than the real `api.anthropic.com`. The link *detection* and
*routing* logic is what these scenarios exercise, and that's upstream-agnostic.

For the plug-pull demo you need **none of this**: start castaway with
`--castaway.link.forced-state=OFFLINE` (or just turn Wi-Fi off) and the runtime
behaves exactly as it does under a real partition.

# ADR-024: k6, open-model workloads, server-side percentiles, and a reproducible lab protocol

- Status: Accepted (WP-03)
- Date: 2026-09-27

## Context

WP-03 must produce defensible numbers: a baseline before any optimisation, comparable before/after runs,
p50/p95/p99, and separate acceptance vs completion latency.

## Decision

1. **Tool: k6** (`grafana/k6:2.3.0`, run as a container on the compose network).
   - Scripts are plain JavaScript in `performance/k6`.
   - `constant-arrival-rate` and `ramping-arrival-rate` executors (**open model**) keep the offered load fixed
     when the system slows down, so overload shows as latency, errors and dropped iterations. A closed model
     (fixed users waiting on responses) quietly lowers its own rate under slowness: coordinated omission.
   - Thresholds are machine-checkable (CI gates).
2. **Percentiles come from the server's histograms in Prometheus**, over exactly the measured window
   (`report.py`). k6's client-side percentiles are reported alongside. Averages are never the headline.
3. **Two latencies, never confused:**

   | Latency | Measured by |
   |---|---|
   | Acceptance (`POST /api/v1/payments`, HTTP 201) | HTTP histogram and k6 |
   | Completion (acceptance → terminal saga step) | `payflow.saga.completion` histogram, server-side, because the saga is asynchronous |

4. **Protocol (reproducible):**
   - Every container has fixed CPU and memory limits (a PayFlow instance = 2 vCPU / 1.5 GiB).
   - Synthetic population: 50 Keycloak users, 5 funded accounts each.
   - Each campaign starts with a 90 s JIT warm-up, which is not measured.
   - A run starts from a quiet system (outbox empty, no open sagas).
   - For overload scenarios on a saturated system, the stack is reset (data volumes wiped) between runs and the
     drain is bounded. Unfinished work at the end is reported, not hidden.
   - Each run produces `performance/results/<run-id>/`: the k6 summary, docker stats samples, and report.json/md.
5. **The same configuration throughout:** tracing at 10 % sampling with export on, JDBC spans on, 5 s Prometheus
   scrape. Observability overhead is inside every number, as it would be in production.

## Alternatives

| Alternative | Why not |
|---|---|
| Gatling | Strong (JVM, open model, HTML reports), a good choice for a Java team. k6 was chosen for container-only execution, simpler CI thresholds, and first-class arrival-rate executors. |
| JMeter | Closed model by default, GUI-centric, XML test plans that review badly. |
| Client-side percentiles only | Include client and network noise and cannot be aggregated across load generators. |

## Trade-offs

- **The lab shares one host.** The load generator, observability stack, Keycloak and an unrelated k3d cluster
  compete for the same 8 vCPU with the system under test. Absolute numbers are "per this lab"; the reports
  compare runs under identical conditions. They do not prove Internet-scale capacity.
- **Synthetic users do not model real retries**, abandoned checkouts or bot traffic.

## CI levels (see CAPACITY-PLAN.md, "CI performance gates")

| Stage | What runs | Gate |
|---|---|---|
| Pull request | Unit, integration, architecture, security tests; **smoke** (5 payments/s, 1 min) | Acceptance p95 < 500 ms, checks > 99 %. Deliberately loose, not brittle. |
| Nightly | normal + peak + burst + degraded | Acceptance p99 < 300 ms; completion p99 < 30 s; zero DLT; regression < 20 % vs the last 7 nightly medians |
| Weekly | soak (45 min+), stress (capacity), hot-account, full failure campaign | Trend review, not a hard gate |

# SLIs and SLOs (WP-03)

**Definitions:**
- An **SLI** is a measured ratio of good events to valid events.
- An **SLO** is the target for that ratio over a window.
- An **SLA** is a contractual promise with penalties. PayFlow has none: SLOs are internal and deliberately
  stricter than any SLA we would sign.

**Status of the targets.** They are **lab assumptions** derived below; they are not production commitments.
"Measured" values come from the WP-03 runs referenced in TUNING-RESULTS.md. An SLO is only marked
"operationally proven" if a run at its load held it; soak and multi-week evidence does not exist yet, so none is
proven over its full window.

## Why these SLIs

A payment service has two different promises:
1. **"I have your payment."** Synchronous acceptance: durable, idempotent, fast.
2. **"Your payment finished correctly."** Asynchronous completion through risk, funds, settlement and capture.

Mixing them (for example "POST latency" as "payment latency") hides the second. They get separate SLIs.

## SLI / SLO table

| # | SLI (good / valid) | Measurement (PromQL source) | SLO (30-day window) | Why this number |
|---|---|---|---|---|
| A1 | **Acceptance availability**: non-5xx `POST /api/v1/payments` / all. 429 counts as good (the caller's quota); 503 admission shedding counts as **bad** (we refused valid work). | `payflow:acceptance_errors:ratio_rate*` | **≥ 99.9 %** | Three nines is achievable with one PostgreSQL primary plus failover (minutes per month). Four nines would need multi-region writes, which this architecture does not have. |
| A2 | **Acceptance latency**: 201 responses under 300 ms / all 201 | exact SLO bucket `le="0.3"` | **≥ 99 % under 300 ms** (p99 < 300 ms) | Checkout UX budget ~1 s end to end; the API gets ~30 %. The baseline normal-load p99 is far below it, so there is headroom for peaks. |
| C1 | **Completion timeliness**: payments reaching COMPLETED within 30 s / all COMPLETED | `payflow:completion_slow:ratio_rate30m` (bucket `le="30.0"`) | **≥ 99 % within 30 s** | The customer sees "processing" and polls. 30 s covers a healthy saga (low seconds) plus one retry cycle (1 s + 3 s + 9 s backoff). |
| C2 | **Completion without human intervention**: terminal outcomes / (terminal + MANUAL_REVIEW escalations) | `payflow:manual_review:ratio_rate1h` | **≥ 99.95 %** (≤ 5 per 10,000 need a human) | Manual review holds customer funds; each case costs operator time. |
| P1 | **Publication timeliness**: events published within 5 s of commit / all | `payflow:publication_slow:ratio_rate30m` (bucket `le="5.0"`) | **≥ 99 % within 5 s** | Leading indicator of C1: publication is the first hop of every saga step. |
| R1 | **Financial integrity**: confirmed CRITICAL reconciliation mismatches | `payflow_reconciliation_mismatches_open{severity="CRITICAL",confirmed="true"}` | **= 0** (invariant, not a budget) | Money misstatement has no acceptable rate. Any occurrence pages. |
| L1 | **Consumer freshness**: main-topic lag not continuously growing for > 10 min | `KafkaConsumerLagGrowing` | no budget; alert only | Diagnostic SLI feeding C1 |

Not SLOs, on purpose:
- **DLT rate:** a symptom, alerted (DeadLettersAppearing).
- **CPU and heap:** causes, not user experience.

## Measurement notes

- **Percentiles are computed from histogram buckets aggregated across replicas** (never averaged per-replica
  percentiles). The SLO thresholds are configured as exact bucket boundaries (`management.metrics.distribution.slo`),
  so "under 300 ms" is exact, not interpolated.
- **C1 counts COMPLETED only.** Rejections (fraud, funds) are correct outcomes with their own latency, and
  including them would flatter the SLI. Stuck payments are not in C1 at all, because they never complete. That is
  why stuck-saga and manual-review alerts exist alongside it.
- **A1 includes admission shedding (503) as bad on purpose.** Shedding protects C1, but it is still unavailability
  to the customer, and spending error budget on it must be visible.

## From SLO to decision

The error budget turns these targets into operational decisions: see ERROR-BUDGET.md.

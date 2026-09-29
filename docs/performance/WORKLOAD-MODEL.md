# Workload model (WP-03)

> **Synthetic lab workload, not production data.** The assumptions below are a plausible mid-size payment service
> provider (PSP), chosen to be defensible and testable on one 8-vCPU lab host. They are not measured customer
> traffic, and nothing in this document claims Internet-scale capacity.

## 1. Business assumptions (labelled assumptions)

| Assumption | Value | Reasoning |
|---|---|---|
| Average business-hours payment rate | **20 payments/s** | about 1.7 M payments/day if sustained; a mid-size PSP |
| Daily peak | **50 payments/s** (2.5 × normal) | typical evening or lunchtime peak ratio for retail payments |
| Flash burst | **150 payments/s for 30 s** (7.5 × normal) | flash sale, payroll batch, marketing push |
| Stress | ramp to 400 payments/s | find the saturation point; no business meaning |
| Soak | normal rate for 45 min (weekly CI), longer ad hoc | leaks, table/index growth, GC drift |
| Hot account | up to 300 payments/s from **one** payer account | merchant payout / corporate treasury account |
| Degraded dependency | normal rate during rail slowness or outage, or Kafka stop | blast radius on acceptance |

## 2. Synthetic population

| Parameter | Value |
|---|---|
| Customers | 50 Keycloak users (`perf-001..050`), provisioned by `performance/scripts/provision-perf-users.sh`; 20 active in standard runs |
| Payer accounts | 5 per customer (100 active), each funded with 10,000,000.00 USD so business declines are not load-dependent |
| Payees | accounts of other customers (payees spread like payers) |
| Hot-account run | one payer account funded with 900,000,000.00 USD; payees spread |
| Amounts | log-uniform 1.00 to 2,000.00 USD: many small, few large, always below the fraud high-amount rule |
| Methods | 70 % CARD, 30 % BANK_TRANSFER (UPI is INR-only in the rail rules and would decline every USD payment) |
| Checkout data | device id, IP address, country US on every payment (no "missing device" risk signal) |

## 3. Request mix (per iteration of the open-model executor)

| Request | Share | Notes |
|---|---|---|
| `POST /api/v1/payments` (write) | 1 per iteration | Idempotency-Key = fresh UUID (no replays) |
| `GET /api/v1/payments/{id}` (read) | 0.5 per iteration (`READ_RATIO`) | customer checks status once, half the time |
| Token refresh (Keycloak) | about 1 per VU per 4 min | not measured as PayFlow latency |

Read : write ratio ≈ **0.5 : 1** at the API. Reads are cheap (primary-key lookup); the expensive work is the
asynchronous saga behind each write.

## 4. Event amplification per payment (derived from the saga, verified against measured rates)

A successful payment (risk approve → reserve → settle → capture) produces:

| Outbox / producer | Messages | Topics |
|---|---|---|
| payment outbox | **8** | PaymentCreated, AssessPaymentRisk, ReserveFunds, PaymentAuthorized, PaymentProcessingStarted, SubmitSettlement, CaptureFunds, PaymentSettled |
| fraud (direct send) | 1 | RiskAssessed |
| account outbox | 2 | FundsReserved, FundsCaptured |
| settlement outbox | 1 | SettlementCompleted |
| **Total produced** | **12** | |
| **Total consumed** | **10** | funds.events is read by two groups; payment.events has no internal consumer |

Per payment in PostgreSQL there are about 10 business transactions:
- create
- 4 saga transitions
- reserve
- settlement intent and outcome (2)
- capture
- ledger post

Plus inbox claims inside them, and relay batches. MongoDB gets 1 insert and 1 velocity count per payment.

**Implication.** One payment puts **8 rows into one outbox**, and each outbox has exactly one active relay
(advisory lock, ADR-010). Payment throughput is therefore bounded by (payment-outbox relay throughput ÷ 8).
Measured in the baseline: the relay published ≈ 98 events/s in total, so the system completes ≈ 10 payments/s,
half the assumed normal load. See WP-03-BASELINE.md and BOTTLENECK-ANALYSIS.md.

## 5. Kafka messages per second at each workload

| Workload | Payments/s | Produced msgs/s | Consumed msgs/s | Payment-outbox rows/s |
|---|---:|---:|---:|---:|
| Normal | 20 | 240 | 200 | 160 |
| Peak | 50 | 600 | 500 | 400 |
| Burst | 150 | 1,800 | 1,500 | 1,200 |
| 10 × normal | 200 | 2,400 | 2,000 | 1,600 |

Declines and compensations change the mix. For example, a rail decline adds PaymentFailed, ReleaseFunds and
FundsReleased. The workload keeps them rare (< 1 %).

## 6. Scenarios (`performance/k6/scenarios`)

| Scenario | Executor | Profile | Duration |
|---|---|---|---|
| smoke | constant-arrival-rate | 5/s | 1 min (PR gate) |
| normal | constant-arrival-rate | 20/s | 10 min |
| peak | ramping-arrival-rate | 20 → 50/s (2 min), hold 6 min, back to 20 | 9 min |
| burst | ramping-arrival-rate | 20/s with two 30 s spikes to 150/s | ~7 min |
| stress | ramping-arrival-rate | 10 → 50 → 100 → 200 → 300 → 400/s | 10 min |
| soak | constant-arrival-rate | 20/s | 45 min |
| hot-account | ramping-arrival-rate | one payer: 50 → 150 → 300/s | 9.5 min |
| degraded | constant-arrival-rate | 20/s while faults are injected | 10 min |

All executors are **open model**: the offered rate does not drop when PayFlow slows down, so overload appears as
latency, errors and `dropped_iterations` (the generator could not start an iteration on time) instead of being
hidden (coordinated omission).

## 7. What this model does not cover

- Real client retry behaviour (clients retrying on 503/429, retry storms from the outside).
- Geographic distribution and network latency: everything is on one host.
- Read-heavy dashboards, merchant list queries, reporting.
- Multi-currency mixes, FX, refunds and chargebacks.
- Long-tail data volume: tables start empty per campaign, while production has months of history. The soak
  run and the relay benchmark partly address growth.

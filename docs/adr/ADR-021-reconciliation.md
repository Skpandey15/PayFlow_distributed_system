# ADR-021: Continuous reconciliation that detects and classifies drift, and never repairs financial data

- Status: Accepted (WP-03, closes WP-02 K4)
- Date: 2026-09-27

## Context

After WP-02, the same money is written by several independently committed records:

| Record | Where |
|---|---|
| Account balance (`available`, `reserved`) | Account |
| Reservations | Account |
| Ledger journals | Ledger (asynchronously, from `funds.events`) |
| Settlement records | Settlement |
| Payment status | Payment |

Tests prove the invariants between them. Nothing watched them in production, so drift was detected only by
indirect signals (a DLT alert). Example: a `FundsCaptured` event dead-lettered and never replayed.

## Decision

1. **A `reconciliation` bounded context** with its own schema (`reconciliation.run`, `reconciliation.mismatch`).
2. **Nine invariants** (`ReconciliationCheck`). Each has a severity and a consistency class:

   | Check | Severity | Consistency |
   |---|---|---|
   | balance.reserved = Σ RESERVED reservations | CRITICAL | same transaction |
   | available + reserved = ledger balance | CRITICAL | eventual |
   | every CAPTURED reservation has a ledger journal (and vice versa) | HIGH / CRITICAL | eventual |
   | every deposit has a journal | HIGH | eventual |
   | SETTLED payment has a COMPLETED settlement | CRITICAL | same flow |
   | COMPLETED settlement belongs to a SETTLED payment, or one still capturing or in review | CRITICAL | eventual |
   | a failed, rejected or cancelled payment holds no funds | HIGH | eventual |
   | the ledger sums to zero per currency | CRITICAL | same transaction |

3. **One consistent snapshot:** every check runs in one `REPEATABLE READ, READ ONLY` transaction, so no false
   mismatch comes from comparing two tables at different moments.
4. **Grace and confirmation:**
   - Eventually consistent checks ignore records changed within the last 2 minutes (events in flight).
   - A mismatch is keyed by (check, subject). It is **confirmed** once seen in two runs, and only confirmed
     mismatches alert.
   - When it is no longer observed, the *finding* is marked RESOLVED.
5. **Single runner:** a transaction-scoped PostgreSQL advisory lock. Every 5 minutes, and on demand via
   `POST /api/v1/ops/reconciliation/runs` (scope `ops:reconciliation`, rate limited).
6. **Never mutate financial records.** The runtime role could technically update account tables, but the
   reconciliation code runs in a READ ONLY transaction for everything it compares and writes only its own schema.
   The workflow is: detect → classify → alert → investigate → controlled correction. Correction happens through
   domain operations or compensating ledger journals approved by finance, which is outside this service.
7. **Observability:**
   - `payflow.reconciliation.mismatches.open{check,severity,confirmed,currency}`
   - `payflow.reconciliation.mismatch.amount{currency,check}`
   - oldest age, runs{result}, duration, records checked.
   - No payment or account ids as labels; they are behind the authenticated ops API.

## Alternatives

| Alternative | Why not |
|---|---|
| Auto-repair ("set balance = ledger") | Which side is right is exactly what is unknown. Repair would hide the bug that caused the drift and could destroy evidence. Rejected. |
| Per-context reconciliation through published use cases | Millions of in-process calls and no single snapshot. Set-based SQL over a consistent snapshot is the only way to get both correctness and cost right. |
| Nightly batch only | Drift would surface a day late. Continuous (5 min) with confirmation gives both speed and no noise. |
| CDC-fed stream reconciliation | Needed at much higher volume. Operational overhead not justified now. |

## Trade-offs

- **It reads four other contexts' schemas.** This is the one documented exception to schema isolation (WP-01
  M4). It is read-only; after extraction it reads a reporting replica or warehouse copy, not the services'
  primaries.
- **Full scans:** the ledger-balance check aggregates the whole ledger each run. Its duration is measured and
  alerted (see BOTTLENECK-ANALYSIS). At scale it must become incremental (per-day watermarks, balance snapshots).

## Operational consequences

- Alerts: `ReconciliationCriticalMismatch` (page, confirmed), `ReconciliationHighMismatch` (ticket, 30 min),
  `ReconciliationNotRunning`.
- Runbook: RUNBOOKS.md#reconciliation-mismatch.

## Failure implications

| Failure | Behaviour |
|---|---|
| A reconciliation bug | Cannot corrupt money (read-only on financial data). The worst case is a false or missed finding. |
| Database overloaded | The run has a 120 s transaction timeout; a failure is counted (`runs{result=FAILED}`) and alerted after 30 minutes without a success. |

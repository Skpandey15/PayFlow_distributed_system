# ADR-015: Funds reservation (available / reserved) in the Account context, protected by row locks and CHECK constraints

- Status: Accepted (WP-02); closes WP-01 finding M2
- Date: 2026-09-27

## Context

WP-01 had no funds control, so balances could go negative. Two concurrent payments from one account must never both reserve the same money. Financial correctness must live in the authoritative store.

## Decision

**Model.** In `account.account_balance(available, reserved)`, per account:

| Operation | Effect |
|---|---|
| Deposit | available += x |
| Reserve (hold) | available −= x, reserved += x |
| Capture | payer reserved −= x, payee available += x |
| Release (compensation) | reserved −= x, available += x |

**Reservation record.** `account.funds_reservation`, one per payment (unique `payment_id`), with states RESERVED → CAPTURED | RELEASED, plus REJECTED. A RELEASED **tombstone** is created when a release overtakes its reserve.

**Concurrency control: pessimistic.**
- Every mutation loads the balance with `SELECT … FOR UPDATE`.
- The reservation is re-read **after** taking the lock, so a concurrent duplicate becomes a no-op.
- Multi-account operations (capture) lock in account-id order, so they cannot deadlock.

**Last line of defence.** `CHECK (available >= 0)` and `CHECK (reserved >= 0)`, which hold even for code that bypasses the domain (tested).

**Ledger reconciliation.** Ledger consumes `FundsCaptured` and `FundsDeposited`. Invariant for customer accounts, once the ledger has caught up:

```
ledger balance == available + reserved
```

This is checked in the end-to-end tests and live.

## Alternatives

| Option | Why not chosen |
|---|---|
| Optimistic locking on the balance row | A hot row for busy accounts, so conflicts lead to retries and retry storms through Kafka. Pessimistic locking serialises cheaply because the transaction is short with no I/O. |
| Conditional UPDATE (`… WHERE available >= x`) without a lock | Atomic and fast, but loses the domain object's invariants and needs a second statement for the reservation record. Viable as a WP-03 optimisation. |
| Holds computed from the ledger (`sum(ledger) − sum(holds)`) | Check-then-act. It needs a lock anyway, and makes every reservation O(history). |
| Redis distributed lock | Not authoritative; lock loss on failover leads to a double spend. Rejected. |

## Trade-offs

- Per-account serialisation of reservations. Throughput per account is bounded by lock hold time, which is milliseconds.
- Two representations of balance (operational `account_balance`, accounting ledger), reconciled by invariant.

## Failure implications

| Case | Behaviour |
|---|---|
| Concurrent reservations | 10 × 30.00 against 100.00 gives exactly 3 RESERVED and 7 INSUFFICIENT_FUNDS; available 10.00, reserved 90.00 (`FundsConcurrencyIT` × 3) |
| Duplicate reserve, concurrent | Held once |
| Duplicate release | No-op |
| Release before reserve | Tombstone, then the late reserve is refused |
| Capture after release | Permanent error, DLT + alert (cannot happen through the saga's own logic) |

## Operational consequences

- A reconciliation job that compares ledger and balances is WP-03.
- Hot merchant accounts are a known 10× risk (lock contention) and will be load-tested.

# Error budgets (WP-03)

**Error budget = 1 − SLO.** It is the amount of unreliability we have agreed to be able to afford in the window.
It is not a formula to admire. It is the input to one recurring decision: **do we spend engineering time on
features or on reliability?**

## Budgets (30-day window = 43,200 minutes)

| SLO | Budget | In time terms | In events terms (at the normal-load assumption, 20 payments/s ≈ 51.8 M payments / 30 d) |
|---|---|---|---|
| A1 acceptance availability 99.9 % | 0.1 % | **43.2 min** of full outage per 30 days (43.8 min for a 30.4-day month) | ≈ 51,800 failed acceptances |
| A2 acceptance latency 99 % < 300 ms | 1 % | n/a (event-based) | ≈ 518,000 slow acceptances |
| C1 completion 99 % < 30 s | 1 % | n/a | ≈ 518,000 slow completions |
| C2 no human intervention 99.95 % | 0.05 % | n/a | ≈ 25,900 manual reviews (in reality any trend above ~10/day is investigated) |
| P1 publication 99 % < 5 s | 1 % | ≈ 7.2 h of fully delayed publication | n/a |
| R1 financial integrity | **none** | zero tolerance | zero |

## Burn-rate alerting (how the budget becomes a page)

**Burn rate** = observed bad ratio ÷ budget ratio. At burn rate 1 the budget lasts exactly 30 days.

| Alert | Condition (A1, budget 0.001) | Consumes | Severity |
|---|---|---|---|
| Fast burn | > 14.4 × over 1 h **and** 5 min | 2 % of the monthly budget in 1 h | page |
| Slow burn | > 6 × over 6 h **and** 30 min | 5 % in 6 h | page |
| Latency burn (A2, budget 0.01) | > 6 × over 1 h and 5 min | | page |

The short window stops the alert from firing long after recovery; the long window stops it firing on a 30-second
blip. Implemented in `deploy/observability/prometheus/rules/payflow-alerts.yml`.

## Policy: what consuming the budget changes

Budget consumption is reviewed weekly, and immediately on any page.

| Consumed (rolling 30 d) | State | Actions |
|---|---|---|
| < 50 % | Healthy | Normal delivery. Risky changes (schema migrations, Kafka config, relay changes) go out behind the standard canary. |
| **≥ 50 %** | Investigate | The owning team explains every burn event in the weekly review. A reliability item for the top burn cause is added to the next sprint. Releases continue, with extra approval for changes on the payment path. |
| **≥ 75 %** | Constrain | **Risky releases freeze** on the payment path (only fixes, security patches and reliability work). Load or failure tests are required for every performance-relevant change. On-call reviews capacity (for example, is the outbox relay near its measured ceiling?). |
| **≥ 100 %** | Exhausted | **Feature freeze** for the affected service until the rolling budget is positive again. Top-priority postmortem action items. Direct escalation to engineering leadership. If the SLO itself is wrong (unachievable with the current architecture), it is renegotiated **explicitly**, not silently ignored. |

Exceptions:
- **Security fixes always ship.**
- **Financial-integrity incidents (R1) trigger the ≥ 100 % response regardless of budget.** A misstated ledger is
  not something we "spend".

## Worked example (from the lab)

In the WP-03 baseline, the assumed normal load (20 payments/s) exceeded what the WP-02 relay could publish.
Completion p99 grew to minutes, so C1 would burn at **≈ 100 ×** (nearly every payment slower than 30 s). The
entire monthly C1 budget would be gone in about 7 hours. Under this policy that is a feature freeze and
reliability work, which is exactly what WP-03 did (ADR-019). The effect is measured in TUNING-RESULTS.md.

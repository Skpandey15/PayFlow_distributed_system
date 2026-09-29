#!/usr/bin/env python3
"""Generates the PayFlow Grafana dashboards (JSON) from one reviewable definition.

Rule: every panel's title is the operational question it answers. No decorative panels.
Run:  python deploy/observability/grafana/generate_dashboards.py
"""
import json
from pathlib import Path

OUT = Path(__file__).parent / "dashboards"
DS = {"type": "prometheus", "uid": "prometheus"}


def ts(title, exprs, unit="short", w=12, h=8, legend=None, stack=False, thresholds=None, desc=None):
    targets = [{"datasource": DS, "expr": e, "legendFormat": (legend[i] if legend else ""), "refId": chr(65 + i)}
               for i, e in enumerate(exprs)]
    p = {"type": "timeseries", "title": title, "datasource": DS, "targets": targets,
         "gridPos": {"w": w, "h": h},
         "fieldConfig": {"defaults": {"unit": unit, "custom": {"lineWidth": 1, "fillOpacity": 10,
                                                                "stacking": {"mode": "normal" if stack else "none"}}},
                         "overrides": []},
         "options": {"legend": {"displayMode": "list", "placement": "bottom"}, "tooltip": {"mode": "multi"}}}
    if thresholds:
        p["fieldConfig"]["defaults"]["thresholds"] = {"mode": "absolute", "steps": [
            {"color": "green", "value": None}] + [{"color": c, "value": v} for v, c in thresholds]}
        p["fieldConfig"]["defaults"]["custom"]["thresholdsStyle"] = {"mode": "line"}
    if desc:
        p["description"] = desc
    return p


def stat(title, expr, unit="short", w=6, h=4, thresholds=None, desc=None):
    p = {"type": "stat", "title": title, "datasource": DS, "gridPos": {"w": w, "h": h},
         "targets": [{"datasource": DS, "expr": expr, "refId": "A", "instant": True}],
         "fieldConfig": {"defaults": {"unit": unit, "thresholds": {"mode": "absolute", "steps": [
             {"color": "green", "value": None}] + [{"color": c, "value": v} for v, c in (thresholds or [])]}},
                         "overrides": []},
         "options": {"colorMode": "background", "reduceOptions": {"calcs": ["lastNotNull"]}}}
    if desc:
        p["description"] = desc
    return p


def q(p, metric, sel="", by=""):
    """histogram_quantile over a 5m rate of a Micrometer histogram."""
    grp = f"le{', ' + by if by else ''}"
    return f'histogram_quantile({p}, sum by ({grp}) (rate({metric}_bucket{{{sel}}}[5m])))'


POST = 'uri="/api/v1/payments",method="POST"'

DASHBOARDS = {
    "payflow-overview": ("PayFlow overview", [
        stat("Are payments being accepted? (201/s)", f'sum(rate(http_server_requests_seconds_count{{{POST},status="201"}}[5m]))', "reqps"),
        stat("Is acceptance within SLO? (p99, 5m)", q(0.99, "http_server_requests_seconds", POST), "s",
             thresholds=[(0.25, "orange"), (0.3, "red")]),
        stat("Are payments completing on time? (saga p99, 5m)", q(0.99, "payflow_saga_completion_seconds", 'outcome="COMPLETED"'), "s",
             thresholds=[(10, "orange"), (30, "red")]),
        stat("Is the error budget burning? (5xx ratio, 1h)",
             f'sum(rate(http_server_requests_seconds_count{{{POST},status=~"5.."}}[1h])) / sum(rate(http_server_requests_seconds_count{{{POST}}}[1h]))',
             "percentunit", thresholds=[(0.001, "orange"), (0.01, "red")]),
        stat("Is publication behind? (oldest unpublished event)", 'max(payflow_outbox_oldest_age_seconds)', "s",
             thresholds=[(15, "orange"), (60, "red")]),
        stat("Does anything need a human? (manual review)", 'max(payflow_saga_open{step="MANUAL_REVIEW"})',
             thresholds=[(1, "orange"), (10, "red")]),
        stat("Is money misstated? (confirmed CRITICAL mismatches)",
             'sum(payflow_reconciliation_mismatches_open{severity="CRITICAL",confirmed="true"}) or vector(0)',
             thresholds=[(1, "red")]),
        stat("Are messages being dead-lettered? (1h)", 'sum(increase(payflow_events_dead_lettered_total[1h])) or vector(0)',
             thresholds=[(1, "orange"), (20, "red")]),
        ts("How do payments end? (terminal outcomes/s)", ['sum by (outcome) (rate(payflow_saga_completion_seconds_count[5m]))'],
           "ops", legend=["{{outcome}}"], stack=True),
        ts("Why are payments taking longer? (where the saga waits, p95 per step)",
           [q(0.95, "payflow_saga_step_duration_seconds", "", "step")], "s", legend=["{{step}}"],
           desc="Compare with outbox age and consumer lag on the same time axis: a rising step with rising outbox age "
                "is a publication problem; with rising lag, a consumer problem; alone, a slow participant."),
    ]),
    "payflow-latency": ("Payment latency", [
        ts("How fast is payment acceptance? (POST /payments p50/p95/p99)",
           [q(p, "http_server_requests_seconds", POST) for p in (0.5, 0.95, 0.99)], "s", legend=["p50", "p95", "p99"],
           thresholds=[(0.3, "red")], w=24),
        ts("How long until a payment completes? (acceptance to terminal, p50/p95/p99)",
           [q(p, "payflow_saga_completion_seconds", 'outcome="COMPLETED"') for p in (0.5, 0.95, 0.99)], "s",
           legend=["p50", "p95", "p99"], thresholds=[(30, "red")]),
        ts("Which saga step is slow? (p99 per step)", [q(0.99, "payflow_saga_step_duration_seconds", "", "step")], "s",
           legend=["{{step}}"]),
        ts("How fast are status reads? (GET /payments/{id} p99)",
           [q(0.99, "http_server_requests_seconds", 'uri="/api/v1/payments/{paymentId}",method="GET"')], "s", legend=["p99"]),
        ts("How fast is the settlement rail? (HTTP client p99 by rail call)",
           [q(0.99, "http_client_requests_seconds", "", "uri")], "s", legend=["{{uri}}"]),
    ]),
    "payflow-kafka": ("Kafka health", [
        ts("Are consumers keeping up? (broker-side lag per group/topic, main topics)",
           ['max by (group, topic) (payflow_kafka_consumer_lag_records{topic!~".*-(retry|dlt).*"})'], legend=["{{group}} {{topic}}"],
           desc="From committed offsets, so it keeps growing when a consumer is dead (the client metric would vanish)."),
        ts("Is the lag monitor itself current? (seconds since last successful read)",
           ['time() - max(payflow_kafka_lag_monitor_last_success_seconds)'], "s", thresholds=[(60, "red")]),
        ts("What is being consumed? (events/s by consumer and outcome)",
           ['sum by (consumer, outcome) (rate(payflow_events_consumed_total[5m]))'], "ops", legend=["{{consumer}} {{outcome}}"]),
        ts("How long does handling an event take? (p99 by consumer)", [q(0.99, "payflow_events_processing_seconds", "", "consumer")],
           "s", legend=["{{consumer}}"]),
        ts("Which failures occur, retryable or permanent? (by category)",
           ['sum by (category) (rate(payflow_events_failed_total[5m]))'], "ops", legend=["{{category}}"]),
        ts("What is being dead-lettered? (by topic and category)",
           ['sum by (topic, category) (increase(payflow_events_dead_lettered_total[15m]))'], legend=["{{topic}} {{category}}"]),
        ts("Is anything stuck in retry topics? (lag on retry/DLT topics)",
           ['max by (group, topic) (payflow_kafka_consumer_lag_records{topic=~".*-retry.*"})'], legend=["{{group}} {{topic}}"]),
    ]),
    "payflow-outbox": ("Outbox health", [
        ts("How old is the oldest unpublished event? (per outbox)", ['max by (outbox) (payflow_outbox_oldest_age_seconds)'], "s",
           legend=["{{outbox}}"], thresholds=[(15, "orange"), (60, "red")],
           desc="The publication SLI. 60 s is also the admission-control threshold (new payments get 503)."),
        ts("Is the backlog growing? (unpublished rows per outbox)", ['max by (outbox) (payflow_outbox_backlog)'],
           legend=["{{outbox}}"]),
        ts("Is publication keeping up with production? (published events/s)", ['sum by (outbox) (rate(payflow_outbox_published_total[1m]))'],
           "ops", legend=["{{outbox}}"]),
        ts("Commit-to-broker delay (p50/p99)", [q(p, "payflow_outbox_publish_delay_seconds") for p in (0.5, 0.99)], "s",
           legend=["p50", "p99"]),
        ts("Is the broker slow or failing? (batch ack wait p99; publish failures/s)",
           [q(0.99, "payflow_outbox_send_latency_seconds", "", "outbox"), 'sum by (outbox) (rate(payflow_outbox_publish_failures_total[5m]))'],
           "short", legend=["ack wait p99 {{outbox}}", "failures/s {{outbox}}"]),
        ts("Is intake being shed? (admission 503 and rate-limit 429 per second)",
           ['sum by (policy) (rate(payflow_traffic_rejected_total[1m]))'], "ops", legend=["{{policy}}"]),
    ]),
    "payflow-saga": ("Saga health", [
        ts("Where are payments right now? (open sagas per step)", ['max by (step) (payflow_saga_open)'], legend=["{{step}}"], stack=True),
        ts("Is anything stuck? (age of the oldest saga per open step)", ['max by (step) (payflow_saga_oldest_age_seconds)'], "s",
           legend=["{{step}}"]),
        ts("Is recovery intervening? (actions per step)", ['sum by (action, step) (increase(payflow_saga_recovery_total[5m]))'],
           legend=["{{action}} {{step}}"]),
        ts("Is recovery holding because publication is behind?", ['sum(increase(payflow_saga_recovery_held_total[5m]))']),
        ts("How often do we compensate?", ['sum(increase(payflow_saga_compensations_total[5m]))']),
        ts("What do operators decide? (manual-review decisions)", ['sum by (decision) (increase(payflow_manual_review_decisions_total[1h]))'],
           legend=["{{decision}}"]),
    ]),
    "payflow-database": ("Database health", [
        ts("Is the connection pool saturated? (active / max / pending)",
           ['max(hikaricp_connections_active)', 'max(hikaricp_connections_max)', 'max(hikaricp_connections_pending)'],
           legend=["active", "max", "pending (threads waiting)"]),
        ts("How long do we wait for a connection? (max, avg)",
           ['max(hikaricp_connections_acquire_seconds_max)',
            'sum(rate(hikaricp_connections_acquire_seconds_sum[5m])) / sum(rate(hikaricp_connections_acquire_seconds_count[5m]))'],
           "s", legend=["max", "avg"]),
        ts("How long is a connection held? (avg usage = transaction length)",
           ['sum(rate(hikaricp_connections_usage_seconds_sum[5m])) / sum(rate(hikaricp_connections_usage_seconds_count[5m]))'], "s"),
        ts("Transactions and rollbacks per second (PostgreSQL)",
           ['sum(rate(pg_stat_database_xact_commit{datname="payflow"}[5m]))', 'sum(rate(pg_stat_database_xact_rollback{datname="payflow"}[5m]))'],
           "ops", legend=["commits", "rollbacks"]),
        ts("Are rows contended? (lock waits by mode; deadlocks)",
           ['sum by (mode) (pg_locks_count{datname="payflow"})', 'sum(increase(pg_stat_database_deadlocks{datname="payflow"}[5m]))'],
           legend=["{{mode}}", "deadlocks"]),
        ts("Backends by state (PostgreSQL side of the pool)", ['sum by (state) (pg_stat_activity_count{datname="payflow"})'],
           legend=["{{state}}"]),
        ts("How fast is MongoDB (fraud store)? (command avg)",
           ['sum(rate(mongodb_driver_commands_seconds_sum[5m])) / sum(rate(mongodb_driver_commands_seconds_count[5m]))'], "s"),
    ]),
    "payflow-jvm": ("JVM health", [
        ts("Is heap pressure rising? (used vs committed vs max)",
           ['sum(jvm_memory_used_bytes{area="heap"})', 'sum(jvm_memory_committed_bytes{area="heap"})', 'sum(jvm_memory_max_bytes{area="heap"})'],
           "bytes", legend=["used", "committed", "max"]),
        ts("Does live data grow? (old generation after GC, a leak signal)",
           ['sum(jvm_memory_used_bytes{area="heap",id=~".*(Old|Tenured).*"})'], "bytes"),
        ts("How long does the application stop for GC? (max pause; total pause per second)",
           ['max(jvm_gc_pause_seconds_max)', 'sum(rate(jvm_gc_pause_seconds_sum[1m]))'], "s", legend=["max pause", "pause time/s"]),
        ts("How fast do we allocate?", ['sum(rate(jvm_gc_memory_allocated_bytes_total[1m]))'], "Bps"),
        ts("CPU (process vs container)", ['max(process_cpu_usage)', 'max(system_cpu_usage)'], "percentunit", legend=["process", "system"]),
        ts("Thread count (platform + virtual carriers)", ['max(jvm_threads_live_threads)', 'max(jvm_threads_peak_threads)'],
           legend=["live", "peak"]),
    ]),
    "payflow-resilience": ("Resilience", [
        ts("Is a settlement rail circuit open? (1 = in state)", ['max by (name, state) (resilience4j_circuitbreaker_state)'],
           legend=["{{name}} {{state}}"]),
        ts("How is the rail answering? (calls by outcome)", ['sum by (rail, outcome) (rate(payflow_settlement_rail_calls_total[5m]))'],
           "ops", legend=["{{rail}} {{outcome}}"]),
        ts("Failure and slow-call rate seen by the breaker", ['max by (name) (resilience4j_circuitbreaker_failure_rate)',
                                                              'max by (name) (resilience4j_circuitbreaker_slow_call_rate)'],
           "percent", legend=["failure {{name}}", "slow {{name}}"]),
        ts("Are retries helping? (retry outcomes)", ['sum by (name, kind) (rate(resilience4j_retry_calls_total[5m]))'], "ops",
           legend=["{{name}} {{kind}}"]),
        ts("Is a bulkhead saturated? (available permits)", ['min by (name) (resilience4j_bulkhead_available_concurrent_calls)'],
           legend=["{{name}}"]),
        ts("Is intake throttled? (429/503 per policy)", ['sum by (policy) (rate(payflow_traffic_rejected_total[1m]))'], "ops",
           legend=["{{policy}}"]),
    ]),
    "payflow-reconciliation": ("Reconciliation", [
        stat("Confirmed CRITICAL drift", 'sum(payflow_reconciliation_mismatches_open{severity="CRITICAL",confirmed="true"}) or vector(0)',
             thresholds=[(1, "red")]),
        stat("Oldest open mismatch", 'max(payflow_reconciliation_mismatch_oldest_age_seconds)', "s", thresholds=[(3600, "orange")]),
        stat("Records checked (last run)", 'max(payflow_reconciliation_records_checked)'),
        stat("Runs failed (24h)", 'sum(increase(payflow_reconciliation_runs_total{result="FAILED"}[24h])) or vector(0)',
             thresholds=[(1, "orange")]),
        ts("Which invariants are violated? (open mismatches by check)",
           ['sum by (check, severity, confirmed) (payflow_reconciliation_mismatches_open)'], legend=["{{check}} {{severity}} confirmed={{confirmed}}"]),
        ts("How much money is misstated? (absolute, per currency)",
           ['sum by (currency) (payflow_reconciliation_mismatch_amount)'], legend=["{{currency}}"]),
        ts("Is reconciliation running and how long does it take?",
           ['sum by (result) (increase(payflow_reconciliation_runs_total[1h]))',
            'max(payflow_reconciliation_duration_seconds_max)'], legend=["{{result}} runs/h", "duration max (s)"]),
    ]),
}


def layout(panels):
    x = y = row_h = 0
    for i, p in enumerate(panels):
        w, h = p["gridPos"]["w"], p["gridPos"]["h"]
        if x + w > 24:
            x, y, row_h = 0, y + row_h, 0
        p["gridPos"].update({"x": x, "y": y})
        p["id"] = i + 1
        x += w
        row_h = max(row_h, h)
    return panels


def main():
    OUT.mkdir(exist_ok=True)
    for uid, (title, panels) in DASHBOARDS.items():
        dash = {"uid": uid, "title": title, "tags": ["payflow", "wp-03"], "timezone": "utc", "refresh": "10s",
                "time": {"from": "now-1h", "to": "now"}, "schemaVersion": 39, "editable": False,
                "panels": layout(panels), "links": [{"title": "PayFlow dashboards", "type": "dashboards", "tags": ["payflow"]}]}
        (OUT / f"{uid}.json").write_text(json.dumps(dash, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {len(DASHBOARDS)} dashboards to {OUT}")


if __name__ == "__main__":
    main()

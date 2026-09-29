#!/usr/bin/env python3
"""Builds report.md / report.json for one load-test run from k6's summary and Prometheus.

Server-side latency percentiles come from Prometheus histograms over exactly the load window, so they are
aggregated from buckets (never averaged percentiles). Anything that could not be measured is reported as null,
never guessed.
"""
import csv
import datetime as dt
import json
import sys
import urllib.parse
import urllib.request
from collections import defaultdict
from pathlib import Path

PROM = "http://localhost:9090"


def iso(s):
    return dt.datetime.fromisoformat(s.replace("Z", "+00:00"))


def q(expr, at):
    url = f"{PROM}/api/v1/query?" + urllib.parse.urlencode({"query": expr, "time": at.timestamp()})
    try:
        with urllib.request.urlopen(url, timeout=30) as r:
            res = json.load(r)["data"]["result"]
    except Exception:  # noqa: BLE001 - the report must still be produced
        return None
    out = {}
    for s in res:
        v = float(s["value"][1])
        if v != v:  # NaN
            continue
        key = ",".join(f"{k}={s['metric'][k]}" for k in sorted(s["metric"]) if k != "__name__")
        out[key or "value"] = round(v, 4)
    return out


def one(expr, at):
    r = q(expr, at)
    if not r:
        return None
    return list(r.values())[0] if len(r) == 1 else r


def ms(v):
    return None if v is None or isinstance(v, dict) else round(v * 1000, 1)


def mb(v):
    return None if v is None or isinstance(v, dict) else round(v / 1048576)


def main():
    out = Path(sys.argv[1])
    drain = int(sys.argv[sys.argv.index("--drain") + 1]) if "--drain" in sys.argv else 0
    k6 = json.loads((out / "k6-summary.json").read_text())
    start = iso(k6["window"]["loadStartedAt"])
    load_end = iso(k6["window"]["loadEndedAt"])
    end = load_end + dt.timedelta(seconds=drain)
    R = f"{int((end - start).total_seconds())}s"
    RL = f"{int((load_end - start).total_seconds())}s"
    secs = RL[:-1]
    m = k6["metrics"]

    def k6v(name, stat):
        return m.get(name, {}).get("values", {}).get(stat)

    post = 'uri="/api/v1/payments",method="POST"'

    def hq(p, sel, metric="http_server_requests_seconds", rng=None, at=None):
        rng = rng or RL
        at = at or load_end
        return one(f"histogram_quantile({p}, sum by (le) (increase({metric}_bucket{{{sel}}}[{rng}])))", at)

    def hq_by(p, by, metric, rng, at):
        return {k: ms(v) for k, v in (q(f"histogram_quantile({p}, sum by (le, {by}) (increase({metric}_bucket[{rng}])))", at) or {}).items()}

    rep = {
        "run": out.name,
        "window": {"start": start.isoformat(), "loadEnd": load_end.isoformat(), "drainEnd": end.isoformat(),
                   "rateScale": k6["window"].get("rateScale"), "durationOverride": k6["window"].get("durationOverride")},
        "client": {
            "iterations": k6v("iterations", "count"),
            "iterations_per_s": k6v("iterations", "rate"),
            "dropped_iterations": k6v("dropped_iterations", "count") or 0,
            "payments_accepted": k6v("payments_accepted", "count"),
            "payments_throttled": k6v("payments_throttled", "count") or 0,
            "payments_rejected_at_api": k6v("payments_rejected_at_api", "count") or 0,
            "checks_pass_rate": k6v("checks", "rate"),
            "post_p50_ms": k6v("http_req_duration{name:POST /api/v1/payments}", "med"),
            "post_p95_ms": k6v("http_req_duration{name:POST /api/v1/payments}", "p(95)"),
            "post_p99_ms": k6v("http_req_duration{name:POST /api/v1/payments}", "p(99)"),
            "post_max_ms": k6v("http_req_duration{name:POST /api/v1/payments}", "max"),
        },
        "server": {
            "accepted_per_s": one(f'sum(increase(http_server_requests_seconds_count{{{post},status="201"}}[{RL}])) / {secs}', load_end),
            "api_5xx_ratio": one(f'sum(increase(http_server_requests_seconds_count{{{post},status=~"5.."}}[{RL}])) / sum(increase(http_server_requests_seconds_count{{{post}}}[{RL}]))', load_end),
            "api_post_p50_ms": ms(hq(0.5, post)),
            "api_post_p95_ms": ms(hq(0.95, post)),
            "api_post_p99_ms": ms(hq(0.99, post)),
            "api_get_p99_ms": ms(hq(0.99, 'uri="/api/v1/payments/{paymentId}",method="GET"')),
            "saga_completed": one(f"sum(increase(payflow_saga_completion_seconds_count[{R}]))", end),
            "saga_completion_by_outcome": q(f"sum by (outcome) (increase(payflow_saga_completion_seconds_count[{R}]))", end),
            "saga_p50_ms": ms(hq(0.5, 'outcome="COMPLETED"', "payflow_saga_completion_seconds", R, end)),
            "saga_p95_ms": ms(hq(0.95, 'outcome="COMPLETED"', "payflow_saga_completion_seconds", R, end)),
            "saga_p99_ms": ms(hq(0.99, 'outcome="COMPLETED"', "payflow_saga_completion_seconds", R, end)),
            "saga_step_p99_ms": hq_by(0.99, "step", "payflow_saga_step_duration_seconds", R, end),
            "drain_seconds_after_load": drain,
            "saga_open_at_load_end": q("max by (step) (payflow_saga_open)", load_end),
            "saga_open_after_drain": q("max by (step) (payflow_saga_open)", end),
            "saga_open_max": q(f"max by (step) (max_over_time(payflow_saga_open[{R}]))", end),
            "outbox_publish_delay_p50_ms": ms(hq(0.5, "", "payflow_outbox_publish_delay_seconds", R, end)),
            "outbox_publish_delay_p99_ms": ms(hq(0.99, "", "payflow_outbox_publish_delay_seconds", R, end)),
            "outbox_publish_delay_p99_by_outbox_ms": hq_by(0.99, "outbox", "payflow_outbox_publish_delay_seconds", R, end),
            "outbox_send_p99_ms": ms(hq(0.99, "", "payflow_outbox_send_latency_seconds", R, end)),
            "outbox_published_per_s": one(f"sum(increase(payflow_outbox_published_total[{RL}])) / {secs}", load_end),
            "outbox_backlog_max": q(f"max by (outbox) (max_over_time(payflow_outbox_backlog[{R}]))", end),
            "outbox_oldest_age_max_s": q(f"max by (outbox) (max_over_time(payflow_outbox_oldest_age_seconds[{R}]))", end),
            "consumer_lag_max_top5": q(f"topk(5, max by (group, topic) (max_over_time(payflow_kafka_consumer_lag_records[{R}])))", end),
            "consumer_p99_ms": hq_by(0.99, "consumer", "payflow_events_processing_seconds", R, end),
            "events_consumed_per_s": one(f"sum(increase(payflow_events_consumed_total[{RL}])) / {secs}", load_end),
            "events_failed": q(f"sum by (category) (increase(payflow_events_failed_total[{R}]))", end),
            "events_dead_lettered": one(f"sum(increase(payflow_events_dead_lettered_total[{R}]))", end) or 0,
            "hikari_active_max": one(f"max(max_over_time(hikaricp_connections_active[{R}]))", end),
            "hikari_pending_max": one(f"max(max_over_time(hikaricp_connections_pending[{R}]))", end),
            "hikari_acquire_max_ms": ms(one(f"max(max_over_time(hikaricp_connections_acquire_seconds_max[{R}]))", end)),
            "hikari_acquire_avg_ms": ms(one(f"sum(increase(hikaricp_connections_acquire_seconds_sum[{R}])) / sum(increase(hikaricp_connections_acquire_seconds_count[{R}]))", end)),
            "hikari_usage_avg_ms": ms(one(f"sum(increase(hikaricp_connections_usage_seconds_sum[{R}])) / sum(increase(hikaricp_connections_usage_seconds_count[{R}]))", end)),
            "hikari_timeouts": one(f"sum(increase(hikaricp_connections_timeout_total[{R}]))", end) or 0,
            "pg_commits_per_s": one(f'sum(rate(pg_stat_database_xact_commit{{datname="payflow"}}[{RL}]))', load_end),
            "pg_rollbacks": one(f'sum(increase(pg_stat_database_xact_rollback{{datname="payflow"}}[{R}]))', end),
            "pg_deadlocks": one(f'sum(increase(pg_stat_database_deadlocks{{datname="payflow"}}[{R}]))', end) or 0,
            "pg_backends_max": one(f'max(max_over_time(pg_stat_database_numbackends{{datname="payflow"}}[{R}]))', end),
            "mongo_cmd_max_ms": ms(one(f"max(max_over_time(mongodb_driver_commands_seconds_max[{R}]))", end)),
            "mongo_cmd_avg_ms": ms(one(f"sum(increase(mongodb_driver_commands_seconds_sum[{R}])) / sum(increase(mongodb_driver_commands_seconds_count[{R}]))", end)),
            "jvm_heap_used_max_mb": mb(one(f'max_over_time(sum(jvm_memory_used_bytes{{area="heap"}})[{R}:5s])', end)),
            "jvm_heap_after_gc_max_mb": mb(one(f'max_over_time(sum(jvm_memory_used_bytes{{area="heap",id=~".*Old.*|.*Tenured.*"}})[{R}:5s])', end)),
            "jvm_heap_committed_max_mb": mb(one(f'max_over_time(sum(jvm_memory_committed_bytes{{area="heap"}})[{R}:5s])', end)),
            "gc_pause_max_ms": ms(one(f"max(max_over_time(jvm_gc_pause_seconds_max[{R}]))", end)),
            "gc_pause_total_ms": ms(one(f"sum(increase(jvm_gc_pause_seconds_sum[{R}]))", end)),
            "gc_count": one(f"sum(increase(jvm_gc_pause_seconds_count[{R}]))", end),
            "alloc_rate_mb_s": (lambda v: None if v is None or isinstance(v, dict) else round(v / 1048576, 1))(
                one(f"sum(rate(jvm_gc_memory_allocated_bytes_total[{RL}]))", load_end)),
            "threads_max": one(f"max(max_over_time(jvm_threads_live_threads[{R}]))", end),
            "process_cpu_avg": one(f"avg(avg_over_time(process_cpu_usage[{RL}]))", load_end),
            "process_cpu_max": one(f"max(max_over_time(process_cpu_usage[{RL}]))", load_end),
        },
    }

    # Container CPU/memory from the docker stats sampler (CPU % is relative to one core).
    stats = defaultdict(lambda: {"cpu": [], "mem": []})
    f = out / "docker-stats.csv"
    if f.exists():
        for row in csv.reader(f.open()):
            if len(row) < 4:
                continue
            try:
                ts = int(row[0])
            except ValueError:
                continue
            name, cpu, mem = row[1], row[2], row[3]
            if not (start.timestamp() <= ts <= load_end.timestamp()):
                continue
            try:
                stats[name]["cpu"].append(float(cpu.rstrip("%")))
                used = mem.split("/")[0].strip()
                num = float("".join(c for c in used if c.isdigit() or c == "."))
                unit = used.lstrip("0123456789.").strip()
                stats[name]["mem"].append(num * {"KiB": 1 / 1024, "MiB": 1, "GiB": 1024, "B": 1 / 1048576}.get(unit, 1))
            except ValueError:
                pass
    rep["containers"] = {n: {"cpu_avg_pct": round(sum(v["cpu"]) / len(v["cpu"]), 1), "cpu_max_pct": max(v["cpu"]),
                             "mem_max_mib": round(max(v["mem"]))}
                         for n, v in stats.items() if v["cpu"] and n.startswith("payflow-")}
    (out / "report.json").write_text(json.dumps(rep, indent=2, default=str))

    lines = [f"# Load-test report: {out.name}", "",
             f"Window: {start.isoformat()} to {load_end.isoformat()} (+{drain}s drain). Rate scale {rep['window']['rateScale']}.", "",
             "## Client (k6, open model)", "", "| Metric | Value |", "|---|---|"]
    lines += [f"| {k} | {v} |" for k, v in rep["client"].items()]
    lines += ["", "## Server (Prometheus)", "", "| Metric | Value |", "|---|---|"]
    lines += [f"| {k} | {json.dumps(v) if isinstance(v, dict) else v} |" for k, v in rep["server"].items()]
    lines += ["", "## Containers (docker stats; CPU % of one core)", "",
              "| Container | CPU avg % | CPU max % | Mem max MiB |", "|---|---|---|---|"]
    lines += [f"| {n} | {v['cpu_avg_pct']} | {v['cpu_max_pct']} | {v['mem_max_mib']} |" for n, v in sorted(rep["containers"].items())]
    (out / "report.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("\n".join(lines))


if __name__ == "__main__":
    main()

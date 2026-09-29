#!/usr/bin/env python3
"""CI performance gate: checks one run's report.json against absolute SLO thresholds and, optionally, against a
reference run (regression). Exit code 1 on failure, so any CI (GitHub Actions, Jenkins) can use it.

   python performance/scripts/ci-gate.py <run-dir> --level pr|nightly [--reference <run-dir>]

Deliberately not brittle:
- Absolute gates are the SLOs, not tight numbers from one lucky run.
- Relative gates allow 20 % (p99) / 15 % (throughput) of noise on a shared CI runner.
- Metrics that are null in either run are reported and skipped, never guessed.
"""
import argparse
import json
import sys
from pathlib import Path

ABSOLUTE = {
    # level: [(json path, comparator, limit, description)]
    "pr": [
        ("client.checks_pass_rate", ">=", 0.99, "requests succeed"),
        ("server.api_post_p95_ms", "<=", 500, "acceptance p95 (smoke, loose)"),
        ("server.events_dead_lettered", "<=", 0, "no dead letters"),
    ],
    "nightly": [
        ("client.checks_pass_rate", ">=", 0.999, "acceptance availability (A1)"),
        ("server.api_post_p99_ms", "<=", 300, "acceptance p99 (A2)"),
        ("server.saga_p99_ms", "<=", 30000, "completion p99 (C1)"),
        ("server.outbox_publish_delay_p99_ms", "<=", 5000, "publication p99 (P1)"),
        ("server.events_dead_lettered", "<=", 0, "no dead letters"),
        ("client.dropped_iterations", "<=", 0, "load generator kept the offered rate"),
    ],
}
RELATIVE = [
    ("server.api_post_p99_ms", "higher_is_worse", 0.20),
    ("server.saga_p99_ms", "higher_is_worse", 0.20),
    ("server.accepted_per_s", "lower_is_worse", 0.15),
    ("server.outbox_published_per_s", "lower_is_worse", 0.15),
]


def get(report, path):
    node = report
    for key in path.split("."):
        if not isinstance(node, dict) or key not in node:
            return None
        node = node[key]
    return node if isinstance(node, (int, float)) else None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("run")
    ap.add_argument("--level", choices=ABSOLUTE.keys(), default="pr")
    ap.add_argument("--reference")
    args = ap.parse_args()
    report = json.loads((Path(args.run) / "report.json").read_text())
    failures = 0
    for path, op, limit, desc in ABSOLUTE[args.level]:
        value = get(report, path)
        if value is None:
            print(f"SKIP  {desc}: {path} not measured")
            continue
        ok = value >= limit if op == ">=" else value <= limit
        print(f"{'PASS' if ok else 'FAIL'}  {desc}: {path} = {value} (limit {op} {limit})")
        failures += not ok
    if args.reference:
        ref = json.loads((Path(args.reference) / "report.json").read_text())
        for path, direction, tolerance in RELATIVE:
            now, before = get(report, path), get(ref, path)
            if now is None or before is None or before == 0:
                print(f"SKIP  regression {path}: not comparable")
                continue
            change = (now - before) / before
            worse = change > tolerance if direction == "higher_is_worse" else change < -tolerance
            print(f"{'FAIL' if worse else 'PASS'}  regression {path}: {before} -> {now} ({change:+.0%}, tolerance {tolerance:.0%})")
            failures += worse
    print(f"---- {failures} gate(s) failed")
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()

#!/usr/bin/env bash
# Starts the stock apache/kafka launcher, then applies identities and ACLs once the broker answers.
# The broker process stays PID-tracked so container stop signals reach it.
set -euo pipefail
/etc/kafka/docker/run &
BROKER=$!
trap 'kill -TERM $BROKER 2>/dev/null; wait $BROKER' TERM INT
bash /opt/payflow/bootstrap-security.sh || { echo "kafka security bootstrap FAILED" >&2; kill -TERM $BROKER; exit 1; }
touch /tmp/security-ready
wait $BROKER

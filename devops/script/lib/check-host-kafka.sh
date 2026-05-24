#!/usr/bin/env bash
# Verify host Kafka (docker-compose) is reachable from Minikube pods.
set -euo pipefail

KAFKA_HOST="${KAFKA_HOST:-host.minikube.internal}"
KAFKA_PORT="${KAFKA_PORT:-9092}"

if ! minikube status >/dev/null 2>&1; then
  echo "WARN: minikube not running — skipping Kafka preflight." >&2
  exit 0
fi

if ! command -v kubectl >/dev/null 2>&1; then
  echo "WARN: kubectl not found — skipping Kafka preflight." >&2
  exit 0
fi

echo "==> Checking Kafka at ${KAFKA_HOST}:${KAFKA_PORT} from a Minikube pod"
if kubectl run minikube-kafka-host-check \
  --rm -i --restart=Never \
  --image=busybox:1.36 \
  --timeout=90s \
  -- sh -c "nc -zvw3 ${KAFKA_HOST} ${KAFKA_PORT}" 2>/dev/null; then
  echo "==> OK: host Kafka reachable from cluster"
  exit 0
fi

echo "" >&2
echo "ERROR: Cannot reach Kafka at ${KAFKA_HOST}:${KAFKA_PORT} from Minikube." >&2
echo "Start Kafka on the host:" >&2
echo "  docker compose -f devops/script/docker-compose-app.yml up -d kafka kafka-ui" >&2
echo "Broker for host apps: localhost:9092" >&2
exit 1

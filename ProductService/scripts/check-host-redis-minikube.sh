#!/usr/bin/env bash
# Verify OS Redis on Ubuntu is reachable from Minikube pods (host.minikube.internal:6379).
# Usage: ./scripts/check-host-redis-minikube.sh
set -euo pipefail

if ! minikube status >/dev/null 2>&1; then
  echo "ERROR: minikube is not running." >&2
  exit 1
fi

if ! command -v kubectl >/dev/null 2>&1; then
  echo "ERROR: kubectl not found." >&2
  exit 1
fi

echo "==> Host: Redis listen addresses (must not be 127.0.0.1-only for Minikube)"
if command -v ss >/dev/null 2>&1; then
  ss -tlnp 2>/dev/null | grep ':6379' || echo "  (no process listening on 6379)"
fi
if ss -tlnp 2>/dev/null | grep ':6379' | grep -q '127.0.0.1:6379' \
   && ! ss -tlnp 2>/dev/null | grep ':6379' | grep -qE '0\.0\.0\.0:6379|\*:6379'; then
  echo "" >&2
  echo "ERROR: Redis listens only on 127.0.0.1 — Minikube cannot use host.minikube.internal." >&2
  echo "Fix /etc/redis/redis.conf (or /etc/redis/redis/redis.conf):" >&2
  echo "  bind 0.0.0.0 -::1" >&2
  echo "  protected-mode no" >&2
  echo "Then: sudo systemctl restart redis-server" >&2
  echo "Verify: ss -tlnp | grep 6379   # expect 0.0.0.0:6379" >&2
  echo "Details: ../../scripts/minikube-host-services.md" >&2
  exit 1
fi

echo "==> Checking Redis at host.minikube.internal:6379 from a Minikube pod"
if kubectl run minikube-redis-host-check \
  --rm -i --restart=Never \
  --image=busybox:1.36 \
  --timeout=90s \
  -- sh -c 'nc -zvw3 host.minikube.internal 6379'; then
  echo "==> OK: host Redis reachable from cluster"
  exit 0
fi

echo "" >&2
echo "ERROR: Pods cannot reach Redis on the host." >&2
echo "OS Redis often listens only on 127.0.0.1 — fix bind/protected-mode and restart:" >&2
echo "  See: ../../scripts/minikube-host-services.md" >&2
echo "  Quick check on host: ss -tlnp | grep 6379" >&2
exit 1

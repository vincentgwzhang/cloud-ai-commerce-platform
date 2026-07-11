#!/usr/bin/env bash
# Remove Minikube metrics NodePort services created by minikube-metrics-apply.sh.
#
# Usage:
#   ./devops/script/observability/minikube-metrics-delete.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEVOPS_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
MANIFEST="${DEVOPS_ROOT}/k8s/observability/metrics-nodeport-services.yaml"

delete_ignored() {
  kubectl delete "$@" --ignore-not-found 2>/dev/null || true
}

if ! command -v kubectl >/dev/null 2>&1; then
  echo "WARN: kubectl not found - nothing to do."
  exit 0
fi

if ! minikube status >/dev/null 2>&1; then
  echo "WARN: minikube is not running - skipping metrics NodePort cleanup."
  exit 0
fi

echo "==> Removing metrics NodePort services"
if [[ -f "${MANIFEST}" ]]; then
  kubectl delete -f "${MANIFEST}" --ignore-not-found
else
  echo "WARN: manifest not found: ${MANIFEST}"
  for svc in auth-service-metrics product-service-metrics inventory-service-metrics \
             order-service-metrics gateway-service-metrics ai-service-metrics; do
    delete_ignored service "${svc}"
  done
fi

echo "==> Metrics NodePort cleanup complete"

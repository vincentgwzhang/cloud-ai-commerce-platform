#!/usr/bin/env bash
set -euo pipefail

DEVOPS_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../lib/paths.sh
source "${DEVOPS_SCRIPT_DIR}/../lib/paths.sh"
devops_init_paths "${BASH_SOURCE[0]}"

delete_ignored() {
  kubectl delete "$@" 2>/dev/null || true
}

if [[ "${MINIKUBE_DEPLOY:-0}" != "1" ]]; then
  # shellcheck source=minikube-lib.sh
  source "${DEVOPS_SCRIPT_SERVICE_DIR}/minikube-lib.sh"
  remove_gateway_service_minikube_images
fi

echo "==> Removing gateway-service workloads"
delete_ignored deployment gateway-service --wait=true --timeout=120s
delete_ignored service gateway-service
delete_ignored configmap gateway-service-config
delete_ignored replicaset -l app=gateway-service
delete_ignored pod -l app=gateway-service

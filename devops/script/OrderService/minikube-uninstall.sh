#!/usr/bin/env bash
set -uo pipefail

DEVOPS_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../lib/paths.sh
source "${DEVOPS_SCRIPT_DIR}/../lib/paths.sh"
devops_init_paths "${BASH_SOURCE[0]}"
# shellcheck source=minikube-lib.sh
source "${DEVOPS_SCRIPT_SERVICE_DIR}/minikube-lib.sh"

REMOVE_MINIKUBE_IMAGE="${REMOVE_MINIKUBE_IMAGE:-1}"

delete_ignored() {
  kubectl delete "$@" --ignore-not-found 2>/dev/null || true
}

if ! command -v kubectl >/dev/null 2>&1; then
  exit 0
fi

if ! minikube status >/dev/null 2>&1; then
  exit 0
fi

echo "==> Removing order-service workloads"
delete_ignored deployment order-service --wait=true --timeout=120s
delete_ignored service order-service
delete_ignored configmap order-service-config
delete_ignored replicaset -l app=order-service
delete_ignored pod -l app=order-service

if [[ "${REMOVE_MINIKUBE_IMAGE}" == "1" ]]; then
  remove_order_service_minikube_images
fi

if [[ "${MINIKUBE_DEPLOY:-0}" == "1" ]]; then
  echo "==> OrderService uninstall phase complete (continuing deploy)"
else
  echo "==> OrderService uninstall done"
fi

#!/usr/bin/env bash
# Teardown ProductService on Minikube (does not remove AuthService secrets).
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
  echo "WARN: kubectl not found — nothing to do."
  exit 0
fi

if ! minikube status >/dev/null 2>&1; then
  echo "WARN: minikube is not running — skipping cluster cleanup."
  exit 0
fi

echo "==> Removing product-service workloads (ignore if absent)"
delete_ignored deployment product-service --wait=true --timeout=120s
delete_ignored service product-service
delete_ignored configmap product-service-config

echo "==> Cleaning up by label app=product-service"
delete_ignored replicaset -l app=product-service
delete_ignored pod -l app=product-service

for _ in $(seq 1 15); do
  remaining="$(kubectl get pods -l app=product-service --no-headers 2>/dev/null | wc -l | tr -d ' ')"
  [[ "${remaining}" == "0" ]] && break
  sleep 2
done

if [[ "${REMOVE_MINIKUBE_IMAGE}" == "1" ]]; then
  remove_product_service_minikube_images
else
  echo "==> Keeping Minikube product-service images (REMOVE_MINIKUBE_IMAGE=0)"
fi

if [[ "${MINIKUBE_DEPLOY:-0}" == "1" ]]; then
  echo "==> ProductService uninstall phase complete (continuing deploy)"
else
  echo "==> ProductService uninstall done (AuthService secrets left intact)"
fi

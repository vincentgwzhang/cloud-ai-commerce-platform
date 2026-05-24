#!/usr/bin/env bash
# Defensive teardown of AuthService on Minikube.
#
# Usage: devops/script/AuthService/minikube-uninstall.sh
#
# Optional:
#   REMOVE_MINIKUBE_IMAGE=0
#   REMOVE_LOCAL_RSA_KEYS=1
#   AUTH_SERVICE_IMAGE=auth-service:1.0.0
set -uo pipefail

DEVOPS_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../lib/paths.sh
source "${DEVOPS_SCRIPT_DIR}/../lib/paths.sh"
devops_init_paths "${BASH_SOURCE[0]}"
# shellcheck source=minikube-lib.sh
source "${DEVOPS_SCRIPT_SERVICE_DIR}/minikube-lib.sh"

REMOVE_MINIKUBE_IMAGE="${REMOVE_MINIKUBE_IMAGE:-1}"
REMOVE_LOCAL_RSA_KEYS="${REMOVE_LOCAL_RSA_KEYS:-0}"

delete_ignored() {
  kubectl delete "$@" --ignore-not-found 2>/dev/null || true
}

if ! command -v kubectl >/dev/null 2>&1; then
  echo "WARN: kubectl not found — nothing to do."
  exit 0
fi

if ! minikube status >/dev/null 2>&1; then
  echo "WARN: minikube is not running — skipping cluster cleanup."
  if [[ "${REMOVE_LOCAL_RSA_KEYS}" == "1" ]]; then
    remove_local_rsa_keys
  fi
  exit 0
fi

echo "==> Removing auth-service workloads (ignore if absent)"
delete_ignored deployment auth-service --wait=true --timeout=120s
delete_ignored service auth-service
delete_ignored configmap auth-service-config
delete_ignored secret auth-service-jwt-keys auth-service-secret

echo "==> Cleaning up by label app=auth-service (orphans / partial deploys)"
delete_ignored replicaset -l app=auth-service
delete_ignored pod -l app=auth-service

echo "==> Waiting for pods to finish terminating (best effort)"
for _ in $(seq 1 15); do
  remaining="$(kubectl get pods -l app=auth-service --no-headers 2>/dev/null | wc -l | tr -d ' ')"
  [[ "${remaining}" == "0" ]] && break
  sleep 2
done

if [[ "${REMOVE_MINIKUBE_IMAGE}" == "1" ]]; then
  remove_auth_service_minikube_images
else
  echo "==> Keeping Minikube auth-service images (REMOVE_MINIKUBE_IMAGE=0)"
fi

if [[ "${REMOVE_LOCAL_RSA_KEYS}" == "1" ]]; then
  remove_local_rsa_keys
else
  echo "==> Keeping local ${JWT_KEYS_DIR}/"
fi

if [[ "${MINIKUBE_DEPLOY:-0}" == "1" ]]; then
  echo "==> AuthService uninstall phase complete (continuing deploy)"
else
  echo "==> AuthService uninstall done"
fi

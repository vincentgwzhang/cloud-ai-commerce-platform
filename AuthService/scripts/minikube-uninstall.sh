#!/usr/bin/env bash
# Defensive teardown of AuthService on Minikube (reverse of minikube-deploy.sh).
# Safe to run even if you never ran minikube-deploy.sh — every step is best-effort.
#
# Usage (from AuthService/): ./scripts/minikube-uninstall.sh
#
# Optional:
#   REMOVE_MINIKUBE_IMAGE=0        keep auth-service images in Minikube (default: remove all auth-service:*)
#   REMOVE_LOCAL_RSA_KEYS=1        delete data/keys/*.pem (next deploy runs generate-rsa-keys.sh)
#   AUTH_SERVICE_IMAGE=auth-service:1.0.0
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
# shellcheck source=minikube-lib.sh
source "${ROOT}/scripts/minikube-lib.sh"

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
  echo "==> Keeping local data/keys/ (deploy will run generate-rsa-keys.sh if missing)"
fi

if [[ "${MINIKUBE_DEPLOY:-0}" == "1" ]]; then
  echo "==> Uninstall phase complete (continuing minikube-deploy.sh)"
else
  echo "==> Done (no resources left is OK; re-run anytime). Deploy with:"
  echo "    ./scripts/minikube-deploy.sh"
fi

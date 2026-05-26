#!/usr/bin/env bash
# Create platform secrets required by the Helm chart (not stored in Git).
#
# Run before the Argo CD Application syncs, or before helm-install.sh deploy step.
#
# Usage:
#   devops/argocd/bootstrap-platform-secrets.sh
#
# Optional env:
#   HELM_NAMESPACE=default
#   DB_USERNAME=vincent
#   DB_PASSWORD=1q2w3e4R
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEVOPS_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
LOCAL_DEV_SETUP="${DEVOPS_ROOT}/script/local-dev-setup.sh"
JWT_KEYS_DIR="${DEVOPS_ROOT}/data/keys"

HELM_NAMESPACE="${HELM_NAMESPACE:-default}"
DB_USERNAME="${DB_USERNAME:-vincent}"
DB_PASSWORD="${DB_PASSWORD:-1q2w3e4R}"

chmod +x "${LOCAL_DEV_SETUP}"
JWT_KEYS_DIR="${JWT_KEYS_DIR}" "${LOCAL_DEV_SETUP}" --keys-only

if [[ ! -f "${JWT_KEYS_DIR}/private.pem" || ! -f "${JWT_KEYS_DIR}/public.pem" ]]; then
  echo "ERROR: missing keys in ${JWT_KEYS_DIR}" >&2
  exit 1
fi

echo "==> Creating secrets in namespace ${HELM_NAMESPACE}"
kubectl create secret generic auth-service-jwt-keys \
  --namespace "${HELM_NAMESPACE}" \
  --from-file=private.pem="${JWT_KEYS_DIR}/private.pem" \
  --from-file=public.pem="${JWT_KEYS_DIR}/public.pem" \
  --dry-run=client -o yaml | kubectl apply -f -

kubectl create secret generic auth-service-secret \
  --namespace "${HELM_NAMESPACE}" \
  --from-literal=DB_USERNAME="${DB_USERNAME}" \
  --from-literal="DB_PASSWORD=${DB_PASSWORD}" \
  --dry-run=client -o yaml | kubectl apply -f -

echo "==> Secrets ready (Argo CD will not manage these)"

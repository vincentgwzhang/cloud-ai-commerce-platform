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
OPENAI_API_KEY="${OPENAI_API_KEY:-}"

secret_key_size() {
  local secret_name="${1:?secret name required}"
  local key_name="${2:?key name required}"

  kubectl get secret "${secret_name}" \
    --namespace "${HELM_NAMESPACE}" \
    -o "jsonpath={.data.${key_name}}" 2>/dev/null \
    | base64 -d 2>/dev/null \
    | wc -c \
    | tr -d ' '
}

verify_secret_key() {
  local secret_name="${1:?secret name required}"
  local key_name="${2:?key name required}"
  local size

  size="$(secret_key_size "${secret_name}" "${key_name}")"
  if [[ "${size}" =~ ^[0-9]+$ && "${size}" -gt 0 ]]; then
    echo "    OK: ${secret_name}/${key_name} exists (${size} bytes)"
  else
    echo "    WARN: ${secret_name}/${key_name} missing or empty" >&2
  fi
}

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

# ai-service OpenAI key (optional). ai-service boots without it; RAG/chat OpenAI calls fail until set.
if [[ -n "${OPENAI_API_KEY}" ]]; then
  echo "==> Creating ai-service-secret (OPENAI_API_KEY provided)"
  kubectl create secret generic ai-service-secret \
    --namespace "${HELM_NAMESPACE}" \
    --from-literal="OPENAI_API_KEY=${OPENAI_API_KEY}" \
    --dry-run=client -o yaml | kubectl apply -f -
else
  echo "==> NOTE: OPENAI_API_KEY not set — skipping ai-service-secret"
  echo "         Set it later: OPENAI_API_KEY=sk-... devops/argocd/bootstrap-platform-secrets.sh"
fi

echo "==> Verifying secret keys (values are not printed)"
verify_secret_key auth-service-jwt-keys private.pem
verify_secret_key auth-service-jwt-keys public.pem
verify_secret_key auth-service-secret DB_USERNAME
verify_secret_key auth-service-secret DB_PASSWORD
if kubectl get secret ai-service-secret --namespace "${HELM_NAMESPACE}" >/dev/null 2>&1; then
  verify_secret_key ai-service-secret OPENAI_API_KEY
else
  echo "    NOTE: ai-service-secret not present (OPENAI_API_KEY is optional)"
fi

echo "==> Secrets ready (Argo CD will not manage these)"

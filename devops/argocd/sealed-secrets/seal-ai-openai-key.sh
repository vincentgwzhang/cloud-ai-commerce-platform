#!/usr/bin/env bash
# Encrypt OPENAI_API_KEY into the Helm values-sealed.yaml using kubeseal.
#
# The output is ciphertext (sealed against this cluster's controller key). It is SAFE to
# commit to Git. The sealed-secrets controller decrypts it into Secret "ai-service-secret".
#
# Usage:
#   OPENAI_API_KEY=sk-... devops/argocd/sealed-secrets/seal-ai-openai-key.sh
#   # or pass as first arg:
#   devops/argocd/sealed-secrets/seal-ai-openai-key.sh sk-...
#
# Optional env:
#   SECRET_NAMESPACE=default            # MUST match the chart namespace (strict scope)
#   SECRET_NAME=ai-service-secret       # MUST match the SealedSecret metadata.name
#   CONTROLLER_NAMESPACE=kube-system
#   CONTROLLER_NAME=sealed-secrets-controller
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEVOPS_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
VALUES_SEALED="${DEVOPS_ROOT}/helm/commerce-platform/values-sealed.yaml"

SECRET_NAMESPACE="${SECRET_NAMESPACE:-default}"
SECRET_NAME="${SECRET_NAME:-ai-service-secret}"
CONTROLLER_NAMESPACE="${CONTROLLER_NAMESPACE:-kube-system}"
CONTROLLER_NAME="${CONTROLLER_NAME:-sealed-secrets-controller}"

OPENAI_API_KEY="${1:-${OPENAI_API_KEY:-}}"

if ! command -v kubeseal >/dev/null 2>&1; then
  echo "ERROR: kubeseal not found. Run: devops/argocd/sealed-secrets/install-sealed-secrets.sh" >&2
  exit 1
fi

if [[ -z "${OPENAI_API_KEY}" ]]; then
  echo "ERROR: OPENAI_API_KEY is empty. Provide it via env var or first argument." >&2
  exit 1
fi

echo "==> Sealing OPENAI_API_KEY (scope=strict, ns=${SECRET_NAMESPACE}, name=${SECRET_NAME})"
# --raw encrypts a single value; strict scope binds it to this namespace + name.
CIPHERTEXT="$(printf '%s' "${OPENAI_API_KEY}" | kubeseal \
  --raw \
  --scope strict \
  --namespace "${SECRET_NAMESPACE}" \
  --name "${SECRET_NAME}" \
  --controller-namespace "${CONTROLLER_NAMESPACE}" \
  --controller-name "${CONTROLLER_NAME}" \
  --from-file=/dev/stdin)"

if [[ -z "${CIPHERTEXT}" ]]; then
  echo "ERROR: kubeseal produced empty output" >&2
  exit 1
fi

echo "==> Writing ${VALUES_SEALED}"
cat > "${VALUES_SEALED}" <<EOF
# AUTO-MANAGED by devops/argocd/sealed-secrets/seal-ai-openai-key.sh
#
# Holds ONLY Sealed Secrets ciphertext (encrypted against your cluster's controller key).
# This is SAFE to commit to Git — it cannot be decrypted without the in-cluster private key.
#
# Referenced by the Argo CD Application as an extra Helm valueFile.
sealedSecrets:
  aiServiceOpenaiKey: "${CIPHERTEXT}"
EOF

echo ""
echo "==> Done. Ciphertext written (key value is NOT in this file in plaintext)."
echo "    Commit + push so Argo CD can sync:"
echo "      git add ${VALUES_SEALED}"
echo "      git commit -m 'chore: seal ai-service OpenAI key'"
echo "      git push"
echo "    Argo CD will sync the SealedSecret; the controller creates Secret ${SECRET_NAME}."

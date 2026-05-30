#!/usr/bin/env bash
# Uninstall the Sealed Secrets (Bitnami) controller from the cluster (idempotent).
#
# Removes the controller, its RBAC, the SealedSecret CRD, and the in-cluster sealing key(s).
# Deletes resources by name so it works without network access.
#
# IMPORTANT: removing the sealing key makes existing ciphertext (values-sealed.yaml)
# permanently undecryptable. After reinstalling you must re-seal the key.
#
# Usage:
#   devops/argocd/sealed-secrets/uninstall-sealed-secrets.sh
#
# Optional env:
#   CONTROLLER_NAMESPACE=kube-system
#   REMOVE_KUBESEAL=1                 # also delete the kubeseal CLI (needs sudo)
#   KUBESEAL_INSTALL_DIR=/usr/local/bin
set -uo pipefail

CONTROLLER_NAMESPACE="${CONTROLLER_NAMESPACE:-kube-system}"
REMOVE_KUBESEAL="${REMOVE_KUBESEAL:-0}"
KUBESEAL_INSTALL_DIR="${KUBESEAL_INSTALL_DIR:-/usr/local/bin}"

delete_ignored() {
  kubectl delete "$@" --ignore-not-found 2>/dev/null || true
}

if ! kubectl cluster-info >/dev/null 2>&1; then
  echo "==> No reachable cluster — skipping in-cluster cleanup"
else
  echo "========================================"
  echo "  Uninstall Sealed Secrets controller (${CONTROLLER_NAMESPACE})"
  echo "========================================"

  echo "==> Removing controller workload + RBAC"
  delete_ignored deployment sealed-secrets-controller -n "${CONTROLLER_NAMESPACE}"
  delete_ignored service sealed-secrets-controller sealed-secrets-controller-metrics -n "${CONTROLLER_NAMESPACE}"
  delete_ignored serviceaccount sealed-secrets-controller -n "${CONTROLLER_NAMESPACE}"
  delete_ignored role sealed-secrets-service-proxier sealed-secrets-key-admin -n "${CONTROLLER_NAMESPACE}"
  delete_ignored rolebinding sealed-secrets-controller sealed-secrets-service-proxier -n "${CONTROLLER_NAMESPACE}"
  delete_ignored clusterrole secrets-unsealer
  delete_ignored clusterrolebinding sealed-secrets-controller

  echo "==> Removing sealing key(s) (renders existing ciphertext undecryptable)"
  delete_ignored secret -l sealedsecrets.bitnami.com/sealed-secrets-key -n "${CONTROLLER_NAMESPACE}"

  echo "==> Removing SealedSecret CRD"
  delete_ignored crd sealedsecrets.bitnami.com
fi

if [[ "${REMOVE_KUBESEAL}" == "1" ]]; then
  echo "==> Removing kubeseal CLI"
  if [[ -f "${KUBESEAL_INSTALL_DIR}/kubeseal" ]]; then
    rm -f "${KUBESEAL_INSTALL_DIR}/kubeseal" 2>/dev/null \
      || sudo rm -f "${KUBESEAL_INSTALL_DIR}/kubeseal"
    echo "    Removed ${KUBESEAL_INSTALL_DIR}/kubeseal"
  fi
else
  echo "==> Keeping kubeseal CLI (set REMOVE_KUBESEAL=1 to remove it)"
fi

echo ""
echo "==> Sealed Secrets uninstalled"
echo "    Reinstall + re-seal:"
echo "      devops/argocd/sealed-secrets/install-sealed-secrets.sh"
echo "      OPENAI_API_KEY=sk-... devops/argocd/sealed-secrets/seal-ai-openai-key.sh"

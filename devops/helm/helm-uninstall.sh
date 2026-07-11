#!/usr/bin/env bash
# Remove commerce-platform Helm release and related Minikube resources (idempotent).
#
# Usage:
#   devops/helm/helm-uninstall.sh
#
# Safe to alternate with helm-install.sh:
#   ./helm-install.sh && ./helm-uninstall.sh && ./helm-install.sh
#
# Removes only Minikube/K8s resources. Does NOT stop MySQL, Redis, or Kafka on the host.
#
# Optional env:
#   HELM_RELEASE=commerce-platform
#   HELM_NAMESPACE=default
#   REMOVE_ARGOCD=1                 # default: uninstall Argo CD too
#   REMOVE_SEALED_SECRETS=1         # default: uninstall Sealed Secrets too
#   REMOVE_MINIKUBE_IMAGES=1        # default: remove service images from Minikube
#   REMOVE_OBSERVABILITY_METRICS=1   # default: delete *-service-metrics NodePorts
set -euo pipefail

HELM_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEVOPS_ROOT="$(cd "${HELM_DIR}/.." && pwd)"
HELM_RELEASE="${HELM_RELEASE:-commerce-platform}"
HELM_NAMESPACE="${HELM_NAMESPACE:-default}"
REMOVE_ARGOCD="${REMOVE_ARGOCD:-1}"
REMOVE_SEALED_SECRETS="${REMOVE_SEALED_SECRETS:-1}"
REMOVE_MINIKUBE_IMAGES="${REMOVE_MINIKUBE_IMAGES:-1}"
REMOVE_OBSERVABILITY_METRICS="${REMOVE_OBSERVABILITY_METRICS:-1}"

delete_ignored() {
  kubectl delete "$@" --ignore-not-found 2>/dev/null || true
}

echo "========================================"
echo "  Helm uninstall (commerce-platform)"
echo "========================================"

if [[ "${REMOVE_ARGOCD}" == "1" ]] && command -v kubectl >/dev/null 2>&1 && minikube status >/dev/null 2>&1; then
  echo "==> Removing Argo CD first (prevents GitOps from recreating resources)"
  "${DEVOPS_ROOT}/argocd/uninstall-argocd.sh"
fi

if command -v helm >/dev/null 2>&1 && minikube status >/dev/null 2>&1; then
  if helm status "${HELM_RELEASE}" -n "${HELM_NAMESPACE}" >/dev/null 2>&1; then
    echo "==> helm uninstall ${HELM_RELEASE} (namespace ${HELM_NAMESPACE})"
    helm uninstall "${HELM_RELEASE}" -n "${HELM_NAMESPACE}" --wait 2>/dev/null || \
      helm uninstall "${HELM_RELEASE}" -n "${HELM_NAMESPACE}" || true
  else
    echo "==> Helm release ${HELM_RELEASE} not found (skip)"
  fi
else
  echo "==> helm or minikube unavailable — skipping helm uninstall"
fi

if command -v kubectl >/dev/null 2>&1 && minikube status >/dev/null 2>&1; then
  echo "==> Removing workloads (Helm or legacy kubectl)"
  for dep in gateway-service ai-service order-service inventory-service product-service auth-service; do
    delete_ignored deployment "${dep}" -n "${HELM_NAMESPACE}" --wait=true --timeout=120s
    delete_ignored service "${dep}" -n "${HELM_NAMESPACE}"
  done
  for cm in gateway-service-config ai-service-config order-service-config inventory-service-config product-service-config auth-service-config; do
    delete_ignored configmap "${cm}" -n "${HELM_NAMESPACE}"
  done
  for app in gateway-service ai-service order-service inventory-service product-service auth-service; do
    delete_ignored replicaset -l "app=${app}" -n "${HELM_NAMESPACE}"
    delete_ignored pod -l "app=${app}" -n "${HELM_NAMESPACE}"
  done

  echo "==> Removing JWT / DB / AI secrets"
  delete_ignored sealedsecret ai-service-secret -n "${HELM_NAMESPACE}"
  delete_ignored secret auth-service-jwt-keys -n "${HELM_NAMESPACE}"
  delete_ignored secret auth-service-secret -n "${HELM_NAMESPACE}"
  delete_ignored secret ai-service-secret -n "${HELM_NAMESPACE}"

  if [[ "${REMOVE_OBSERVABILITY_METRICS}" == "1" ]]; then
    echo "==> Removing observability metrics NodePort services (not part of Helm chart)"
    for svc in auth-service-metrics product-service-metrics inventory-service-metrics \
               order-service-metrics gateway-service-metrics ai-service-metrics; do
      delete_ignored service "${svc}" -n "${HELM_NAMESPACE}"
    done
  fi
fi

if [[ "${REMOVE_SEALED_SECRETS}" == "1" ]] && command -v kubectl >/dev/null 2>&1 && minikube status >/dev/null 2>&1; then
  echo "==> Removing Sealed Secrets controller / CRD / sealing key"
  "${DEVOPS_ROOT}/argocd/sealed-secrets/uninstall-sealed-secrets.sh"
fi

if [[ "${REMOVE_MINIKUBE_IMAGES}" == "1" ]] && minikube status >/dev/null 2>&1; then
  echo "==> Removing service images from Minikube (best effort)"
  for tag in \
    auth-service:1.0.0 product-service:1.0.0 inventory-service:1.0.0 order-service:1.0.0 gateway-service:1.0.0 ai-service:1.0.0; do
    minikube image rm "${tag}" 2>/dev/null || true
  done
fi

echo ""
echo "==> Helm uninstall complete"
echo "    Argo CD, Sealed Secrets, platform workloads, metrics, secrets, and service images were removed."
echo "    Host MySQL / Redis / Kafka / Chroma were not touched."
echo "    Redeploy: OPENAI_API_KEY=sk-... ${HELM_DIR}/helm-install.sh"

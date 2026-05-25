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
#   REMOVE_MINIKUBE_IMAGES=1
set -euo pipefail

HELM_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HELM_RELEASE="${HELM_RELEASE:-commerce-platform}"
HELM_NAMESPACE="${HELM_NAMESPACE:-default}"
REMOVE_MINIKUBE_IMAGES="${REMOVE_MINIKUBE_IMAGES:-0}"

delete_ignored() {
  kubectl delete "$@" --ignore-not-found 2>/dev/null || true
}

echo "========================================"
echo "  Helm uninstall (commerce-platform)"
echo "========================================"

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
  for dep in gateway-service order-service inventory-service product-service auth-service; do
    delete_ignored deployment "${dep}" -n "${HELM_NAMESPACE}" --wait=true --timeout=120s
    delete_ignored service "${dep}" -n "${HELM_NAMESPACE}"
  done
  for cm in gateway-service-config order-service-config inventory-service-config product-service-config auth-service-config; do
    delete_ignored configmap "${cm}" -n "${HELM_NAMESPACE}"
  done
  for app in gateway-service order-service inventory-service product-service auth-service; do
    delete_ignored replicaset -l "app=${app}" -n "${HELM_NAMESPACE}"
    delete_ignored pod -l "app=${app}" -n "${HELM_NAMESPACE}"
  done

  echo "==> Removing JWT / DB secrets"
  delete_ignored secret auth-service-jwt-keys -n "${HELM_NAMESPACE}"
  delete_ignored secret auth-service-secret -n "${HELM_NAMESPACE}"
fi

if [[ "${REMOVE_MINIKUBE_IMAGES}" == "1" ]] && minikube status >/dev/null 2>&1; then
  echo "==> Removing service images from Minikube (best effort)"
  for tag in \
    auth-service:1.0.0 product-service:1.0.0 inventory-service:1.0.0 order-service:1.0.0 gateway-service:1.0.0; do
    minikube image rm "${tag}" 2>/dev/null || true
  done
fi

echo ""
echo "==> Helm uninstall complete"
echo "    Redeploy: ${HELM_DIR}/helm-install.sh"

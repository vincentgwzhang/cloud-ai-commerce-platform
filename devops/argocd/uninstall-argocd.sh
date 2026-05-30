#!/usr/bin/env bash
# Uninstall Argo CD from the cluster (idempotent).
#
# Removes the Argo CD Application(s), then all Argo CD components, the argocd namespace,
# and leftover cluster-scoped resources (CRDs / ClusterRoles).
#
# It does NOT delete your platform workloads — the finalizer is stripped first so removing
# Argo CD does not cascade-delete auth/product/.../ai. Use devops/helm/helm-uninstall.sh for those.
#
# Usage:
#   devops/argocd/uninstall-argocd.sh
#
# Optional env:
#   ARGOCD_VERSION=stable          # must match what you installed (for delete -f)
#   ARGOCD_NAMESPACE=argocd
set -uo pipefail

ARGOCD_VERSION="${ARGOCD_VERSION:-stable}"
ARGOCD_NAMESPACE="${ARGOCD_NAMESPACE:-argocd}"
ARGOCD_INSTALL_URL="https://raw.githubusercontent.com/argoproj/argo-cd/${ARGOCD_VERSION}/manifests/install.yaml"

delete_ignored() {
  kubectl delete "$@" --ignore-not-found 2>/dev/null || true
}

if ! kubectl cluster-info >/dev/null 2>&1; then
  echo "==> No reachable cluster — nothing to do"
  exit 0
fi

echo "========================================"
echo "  Uninstall Argo CD (namespace ${ARGOCD_NAMESPACE})"
echo "========================================"

# 1) Remove Applications WITHOUT cascade. Stripping the finalizer prevents both a hang
#    (if the controller is already gone) and an unintended cascade-delete of platform workloads.
echo "==> Removing Argo CD Applications (non-cascading)"
for app in $(kubectl get application -n "${ARGOCD_NAMESPACE}" -o name 2>/dev/null); do
  kubectl patch "${app}" -n "${ARGOCD_NAMESPACE}" \
    -p '{"metadata":{"finalizers":[]}}' --type merge 2>/dev/null || true
  delete_ignored "${app}" -n "${ARGOCD_NAMESPACE}"
done

# 2) Delete the Argo CD install manifest (components + CRDs + cluster roles). Best effort (needs network).
echo "==> Deleting Argo CD components (${ARGOCD_VERSION})"
kubectl delete -n "${ARGOCD_NAMESPACE}" -f "${ARGOCD_INSTALL_URL}" --ignore-not-found 2>/dev/null \
  || echo "    (delete -f skipped/failed — falling back to namespace + CRD cleanup)"

# 3) Delete the namespace (removes any namespaced remnants).
echo "==> Deleting namespace ${ARGOCD_NAMESPACE}"
delete_ignored namespace "${ARGOCD_NAMESPACE}"

# 4) Cluster-scoped leftovers (in case delete -f could not run).
echo "==> Removing leftover Argo CD CRDs / cluster roles"
for crd in $(kubectl get crd -o name 2>/dev/null | grep 'argoproj.io'); do
  delete_ignored "${crd}"
done
for cr in $(kubectl get clusterrole -o name 2>/dev/null | grep 'argocd'); do
  delete_ignored "${cr}"
done
for crb in $(kubectl get clusterrolebinding -o name 2>/dev/null | grep 'argocd'); do
  delete_ignored "${crb}"
done

echo ""
echo "==> Argo CD uninstalled"
echo "    Platform workloads (if any) were left running and are no longer GitOps-managed."
echo "    Reinstall: devops/argocd/install-argocd.sh"

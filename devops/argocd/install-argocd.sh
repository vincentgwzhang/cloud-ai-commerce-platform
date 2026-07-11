#!/usr/bin/env bash
# Install Argo CD into the current cluster (Minikube).
#
# Usage:
#   devops/argocd/install-argocd.sh
#
# UI:
#   kubectl port-forward svc/argocd-server -n argocd 8080:443
#   https://localhost:8080  (user: admin, password below)
set -euo pipefail

ARGOCD_VERSION="${ARGOCD_VERSION:-stable}"
ARGOCD_INSTALL_URL="https://raw.githubusercontent.com/argoproj/argo-cd/${ARGOCD_VERSION}/manifests/install.yaml"

if ! kubectl cluster-info >/dev/null 2>&1; then
  echo "ERROR: kubectl cannot reach a cluster" >&2
  exit 1
fi

echo "==> Applying Argo CD (${ARGOCD_VERSION})"
kubectl create namespace argocd --dry-run=client -o yaml | kubectl apply -f -

# Client-side apply stores the full manifest in metadata.annotations (last-applied-configuration).
# Argo CD CRDs (e.g. applicationsets.argoproj.io) exceed the 256KiB limit — use server-side apply.
echo "==> Using server-side apply (avoids CRD annotation size limit on K8s 1.29+)"
kubectl apply -n argocd -f "${ARGOCD_INSTALL_URL}" \
  --server-side \
  --force-conflicts

echo "==> Waiting for argocd-server deployment"
kubectl rollout status deployment/argocd-server -n argocd --timeout=300s

echo ""
echo "==> Argo CD installed"
echo "    UI:  kubectl port-forward svc/argocd-server -n argocd 28080:443"
echo "    User: admin"
echo "    Pass: kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 -d; echo"
echo ""
echo "    Next: edit devops/argocd/applications/commerce-platform.application.yaml (repoURL)"
echo "          devops/argocd/bootstrap-platform-secrets.sh"
echo "          kubectl apply -f devops/argocd/applications/commerce-platform.application.yaml"

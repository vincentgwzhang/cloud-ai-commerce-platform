#!/usr/bin/env bash
# Package the full cloud-ai-commerce-platform Helm chart (all 5 microservices).
#
# Usage (from repo root or devops/helm):
#   devops/helm/helm-package.sh
#
# Output: devops/helm/dist/commerce-platform-<version>.tgz
# Then:   devops/helm/helm-install.sh
set -euo pipefail

HELM_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CHART_DIR="${HELM_DIR}/commerce-platform"
DIST_DIR="${HELM_DIR}/dist"
TEMPLATE_DIR="${CHART_DIR}/templates"

# Must match services built by helm-install.sh
REQUIRED_DEPLOYMENTS=(
  auth-service-deployment.yaml
  product-service-deployment.yaml
  inventory-service-deployment.yaml
  order-service-deployment.yaml
  gateway-service-deployment.yaml
  ai-service-deployment.yaml
)

if ! command -v helm >/dev/null 2>&1; then
  echo "ERROR: helm not found" >&2
  exit 1
fi

if [[ ! -f "${CHART_DIR}/Chart.yaml" ]]; then
  echo "ERROR: chart not found at ${CHART_DIR}" >&2
  exit 1
fi

echo "==> Verifying full platform chart templates"
for f in "${REQUIRED_DEPLOYMENTS[@]}"; do
  if [[ ! -f "${TEMPLATE_DIR}/${f}" ]]; then
    echo "ERROR: missing ${TEMPLATE_DIR}/${f}" >&2
    exit 1
  fi
done
echo "    OK: auth, product, inventory, order, gateway, ai"

mkdir -p "${DIST_DIR}"

echo "==> helm lint ${CHART_DIR}"
helm lint "${CHART_DIR}"

echo "==> helm template (smoke render)"
helm template commerce-platform "${CHART_DIR}" >/dev/null

echo "==> helm package -> ${DIST_DIR}"
helm package "${CHART_DIR}" -d "${DIST_DIR}"

PKG="$(ls -1t "${DIST_DIR}"/commerce-platform-*.tgz 2>/dev/null | head -1)"
echo ""
echo "========================================"
echo "  Platform chart packaged"
echo "========================================"
echo "  Chart:   ${PKG}"
echo "  Includes: Auth, Product, Inventory, Order, Gateway, AI"
echo "  Deploy:  ${HELM_DIR}/helm-install.sh"
echo "  Note:    install deploys K8s only; you start MySQL/Redis/Kafka/Chroma on the host"
echo "           install builds Docker images for the 6 microservices (SKIP_BUILD=1 to skip)"

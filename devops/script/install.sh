#!/usr/bin/env bash
# One-shot Minikube install: uninstall everything, then AuthService + ProductService.
#
# Usage:
#   devops/script/install.sh
#
# Idempotent with uninstall.sh — you can alternate:
#   ./install.sh && ./uninstall.sh && ./install.sh
#
# Optional env (passed through to service scripts):
#   DB_PASSWORD, AUTH_SERVICE_IMAGE, PRODUCT_SERVICE_IMAGE
#   FORCE_RSA_REGENERATE=1, SKIP_HOST_REDIS_CHECK=1
set -euo pipefail

SCRIPT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if ! minikube status >/dev/null 2>&1; then
  echo "ERROR: minikube is not running. Start with: minikube start" >&2
  exit 1
fi

echo "========================================"
echo "  Minikube install (all services)"
echo "========================================"

echo ""
echo "==> Ensuring JWT keys (devops/data/keys)"
chmod +x "${SCRIPT_ROOT}/local-dev-setup.sh"
"${SCRIPT_ROOT}/local-dev-setup.sh" --keys-only

echo ""
echo "==> Step 0: uninstall existing workloads"
"${SCRIPT_ROOT}/uninstall.sh"

echo ""
echo "==> Step 1: deploy AuthService"
export MINIKUBE_SKIP_UNINSTALL=1
"${SCRIPT_ROOT}/AuthService/minikube-deploy.sh"

echo ""
echo "==> Step 2: deploy ProductService"
export MINIKUBE_SKIP_UNINSTALL=1
"${SCRIPT_ROOT}/ProductService/minikube-deploy.sh"

echo ""
echo "==> Step 3: deploy InventoryService"
export MINIKUBE_SKIP_UNINSTALL=1
"${SCRIPT_ROOT}/InventoryService/minikube-deploy.sh"

echo ""
echo "==> Step 4: deploy OrderService"
export MINIKUBE_SKIP_UNINSTALL=1
"${SCRIPT_ROOT}/OrderService/minikube-deploy.sh"

echo ""
echo "========================================"
echo "  Install complete"
echo "========================================"
echo "  Auth:      minikube service auth-service --url"
echo "             kubectl port-forward svc/auth-service 8080:80"
echo "  Product:   minikube service product-service --url"
echo "             kubectl port-forward svc/product-service 8081:80"
echo "  Inventory: minikube service inventory-service --url"
echo "             kubectl port-forward svc/inventory-service 8082:80"
echo "  Order:     minikube service order-service --url"
echo "             kubectl port-forward svc/order-service 8083:80"
echo ""
echo "  Kafka:     localhost:9092 (devops/script/docker-compose-app.yml)"
echo ""
echo "  Teardown: ${SCRIPT_ROOT}/uninstall.sh"

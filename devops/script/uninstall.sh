#!/usr/bin/env bash
# Remove all Minikube workloads for ProductService + AuthService (idempotent).
#
# Usage (from repo root or anywhere):
#   devops/script/uninstall.sh
#
# Safe to run repeatedly. Order: Product first, then Auth (secrets).
set -uo pipefail

SCRIPT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "========================================"
echo "  Minikube uninstall (all services)"
echo "========================================"

"${SCRIPT_ROOT}/GatewayService/minikube-uninstall.sh"
"${SCRIPT_ROOT}/OrderService/minikube-uninstall.sh"
"${SCRIPT_ROOT}/InventoryService/minikube-uninstall.sh"
"${SCRIPT_ROOT}/ProductService/minikube-uninstall.sh"
"${SCRIPT_ROOT}/AuthService/minikube-uninstall.sh"

echo ""
echo "==> All platform workloads removed from Minikube"
echo "    Redeploy: ${SCRIPT_ROOT}/install.sh"

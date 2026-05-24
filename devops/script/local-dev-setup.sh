#!/usr/bin/env bash
# One-time / repeat setup for local IntelliJ: JWT keys + checklist.
# Keys live in devops/data/keys/ (shared by AuthService, ProductService, Minikube, Docker).
#
# Usage:
#   ./devops/script/local-dev-setup.sh           # keys + dev checklist
#   ./devops/script/local-dev-setup.sh --keys-only   # generate keys only (install.sh, minikube)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
DEVOPS_ROOT="${REPO_ROOT}/devops"
KEY_DIR="${JWT_KEYS_DIR:-${DEVOPS_ROOT}/data/keys}"

KEYS_ONLY=0
if [[ "${1:-}" == "--keys-only" ]]; then
  KEYS_ONLY=1
fi

generate_jwt_keys_if_missing() {
  mkdir -p "${KEY_DIR}"

  if [[ -f "${KEY_DIR}/private.pem" && -f "${KEY_DIR}/public.pem" ]]; then
    echo "RSA keys already exist in ${KEY_DIR}"
    return 0
  fi

  openssl genpkey -algorithm RSA -out "${KEY_DIR}/private.pem" -pkeyopt rsa_keygen_bits:2048
  openssl pkey -in "${KEY_DIR}/private.pem" -pubout -out "${KEY_DIR}/public.pem"

  chmod 600 "${KEY_DIR}/private.pem"
  chmod 644 "${KEY_DIR}/public.pem"

  echo "Generated RSA key pair:"
  echo "  ${KEY_DIR}/private.pem"
  echo "  ${KEY_DIR}/public.pem"
}

echo "==> JWT keys (${KEY_DIR})"
generate_jwt_keys_if_missing

if [[ "${KEYS_ONLY}" -eq 1 ]]; then
  exit 0
fi

echo ""
echo "==> Local dev setup (IntelliJ)"
echo "    Repo: ${REPO_ROOT}"
echo ""
echo "==> Prerequisites (manual if not already running)"
echo "    MySQL:  database commerce_platform, user vincent"
echo "            mysql -u vincent -p commerce_platform < ${DEVOPS_ROOT}/db/init.sql"
echo "    Redis:  localhost:6379 (OS install; see ${DEVOPS_ROOT}/docs/minikube-host-services.md)"
echo "    Products table: Flyway on first ProductService start"
echo ""
echo "==> IntelliJ run order"
echo "    1. AuthService [local]     → http://localhost:8080"
echo "    2. ProductService [local]  → http://localhost:8081"
echo "    3. Postman: Login (Auth), then List Products"
echo ""
echo "    Both services use devops/data/keys (profile local: ../devops/data/keys/*.pem)"
echo ""
echo "==> Minikube"
echo "    ${SCRIPT_DIR}/install.sh"
echo "    ${SCRIPT_DIR}/uninstall.sh"

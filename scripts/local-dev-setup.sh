#!/usr/bin/env bash
# One-time / repeat setup for local IntelliJ: AuthService (8080) + ProductService (8081).
# Does not start Minikube or Docker app containers — those keep their own scripts under AuthService/scripts/.
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
AUTH="${REPO}/AuthService"
PRODUCT="${REPO}/ProductService"

echo "==> Local dev setup (IntelliJ)"
echo "    Repo: ${REPO}"

if [[ ! -f "${AUTH}/scripts/generate-rsa-keys.sh" ]]; then
  echo "ERROR: AuthService not found at ${AUTH}" >&2
  exit 1
fi

chmod +x "${AUTH}/scripts/generate-rsa-keys.sh" \
  "${PRODUCT}/scripts/sync-jwt-public-key.sh" 2>/dev/null || true

echo "==> JWT keys (AuthService/data/keys)"
if [[ ! -f "${AUTH}/data/keys/private.pem" || ! -f "${AUTH}/data/keys/public.pem" ]]; then
  (cd "${AUTH}" && ./scripts/generate-rsa-keys.sh)
else
  echo "    Keys already present — skip generate (delete keys + re-run to rotate)"
fi

echo "==> JWT public key for ProductService (IntelliJ + Docker)"
mkdir -p "${PRODUCT}/data/keys"
cp "${AUTH}/data/keys/public.pem" "${PRODUCT}/data/keys/public.pem"
echo "    ${PRODUCT}/data/keys/public.pem"

echo ""
echo "==> Prerequisites (manual if not already running)"
echo "    MySQL:  database commerce_platform, user vincent"
echo "            mysql -u vincent -p commerce_platform < ${AUTH}/sql/init.sql"
echo "    Redis:  localhost:6379 (e.g. docker run -d --name redis -p 6379:6379 redis:7-alpine)"
echo "    Products table: created by ProductService Flyway on first start"
echo ""
echo "==> IntelliJ run order"
echo "    1. Run configuration: AuthService [local]     → http://localhost:8080"
echo "    2. Run configuration: ProductService [local]  → http://localhost:8081"
echo "    3. Postman: Auth Service → Login, then Product Service → List Products"
echo ""
echo "    Working directories: AuthService/ and ProductService/ (see .run/*.run.xml)"
echo ""
echo "Other workflows (unchanged):"
echo "    AuthService Docker:  cd AuthService && ./scripts/docker-run.sh"
echo "    AuthService Minikube: cd AuthService && ./scripts/minikube-deploy.sh"

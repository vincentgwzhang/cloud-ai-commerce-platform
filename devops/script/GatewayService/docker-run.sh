#!/usr/bin/env bash
set -euo pipefail

DEVOPS_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../lib/paths.sh
source "${DEVOPS_SCRIPT_DIR}/../lib/paths.sh"
devops_init_paths "${BASH_SOURCE[0]}"
# shellcheck source=minikube-lib.sh
source "${DEVOPS_SCRIPT_SERVICE_DIR}/minikube-lib.sh"

"${DEVOPS_ROOT}/script/local-dev-setup.sh" --keys-only
ensure_jwt_public_key

cd "${SERVICE_ROOT}"
IMAGE="${GATEWAY_SERVICE_IMAGE:-gateway-service:1.0.0}"
CONTAINER_NAME="${GATEWAY_SERVICE_CONTAINER:-gateway-service}"
HOST_PORT="${HOST_PORT:-8088}"

docker rm -f "${CONTAINER_NAME}" >/dev/null 2>&1 || true
docker rmi -f "${IMAGE}" >/dev/null 2>&1 || true
mvn -DskipTests package
cd "${REPO_ROOT}"
docker build -f "${SERVICE_ROOT}/Dockerfile" -t "${IMAGE}" .

exec docker run --rm \
  --name "${CONTAINER_NAME}" \
  -p "${HOST_PORT}:8088" \
  --add-host=host.docker.internal:host-gateway \
  -e SPRING_PROFILES_ACTIVE=docker \
  -e "JWT_PUBLIC_KEY_PATH=file:/app/keys/public.pem" \
  -e "PRODUCT_SERVICE_URI=${PRODUCT_SERVICE_URI:-http://host.docker.internal:8081}" \
  -e "INVENTORY_SERVICE_URI=${INVENTORY_SERVICE_URI:-http://host.docker.internal:8082}" \
  -e "ORDER_SERVICE_URI=${ORDER_SERVICE_URI:-http://host.docker.internal:8083}" \
  "${IMAGE}"

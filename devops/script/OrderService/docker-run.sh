#!/usr/bin/env bash
set -euo pipefail

DEVOPS_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../lib/paths.sh
source "${DEVOPS_SCRIPT_DIR}/../lib/paths.sh"
devops_init_paths "${BASH_SOURCE[0]}"

JWT_KEYS_DIR="${JWT_KEYS_DIR}" "${DEVOPS_ROOT}/script/local-dev-setup.sh" --keys-only
ensure_jwt_public_key

cd "${SERVICE_ROOT}"
IMAGE="${ORDER_SERVICE_IMAGE:-order-service:1.0.0}"
CONTAINER_NAME="${ORDER_SERVICE_CONTAINER:-order-service}"
HOST_PORT="${HOST_PORT:-8083}"

docker rm -f "${CONTAINER_NAME}" >/dev/null 2>&1 || true
docker rmi -f "${IMAGE}" >/dev/null 2>&1 || true
mvn -DskipTests package
cd "${REPO_ROOT}"
docker build -f "${SERVICE_ROOT}/Dockerfile" -t "${IMAGE}" .

exec docker run --rm \
  --name "${CONTAINER_NAME}" \
  -p "${HOST_PORT}:8083" \
  --add-host=host.docker.internal:host-gateway \
  -e SPRING_PROFILES_ACTIVE=docker \
  -e "DB_HOST=${DB_HOST:-host.docker.internal}" \
  -e "REDIS_HOST=${REDIS_HOST:-host.docker.internal}" \
  -e "KAFKA_BOOTSTRAP_SERVERS=${KAFKA_BOOTSTRAP_SERVERS:-host.docker.internal:9092}" \
  -e "JWT_PUBLIC_KEY_PATH=file:/app/keys/public.pem" \
  "${IMAGE}"

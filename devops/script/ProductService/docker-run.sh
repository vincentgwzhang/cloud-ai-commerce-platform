#!/usr/bin/env bash
set -euo pipefail

DEVOPS_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../lib/paths.sh
source "${DEVOPS_SCRIPT_DIR}/../lib/paths.sh"
devops_init_paths "${BASH_SOURCE[0]}"

ensure_jwt_public_key

cd "${SERVICE_ROOT}"

IMAGE="${PRODUCT_SERVICE_IMAGE:-product-service:1.0.0}"
CONTAINER_NAME="${PRODUCT_SERVICE_CONTAINER:-product-service}"
HOST_PORT="${HOST_PORT:-8081}"

docker rm -f "${CONTAINER_NAME}" >/dev/null 2>&1 || true
docker rmi -f "${IMAGE}" >/dev/null 2>&1 || true

echo "Building JAR..."
mvn -DskipTests package

echo "Building Docker image '${IMAGE}'..."
cd "${REPO_ROOT}"
docker build -f "${SERVICE_ROOT}/Dockerfile" -t "${IMAGE}" .

echo "Starting container '${CONTAINER_NAME}' on port ${HOST_PORT}..."
exec docker run --rm \
  --name "${CONTAINER_NAME}" \
  -p "${HOST_PORT}:8081" \
  --add-host=host.docker.internal:host-gateway \
  -e SPRING_PROFILES_ACTIVE=docker \
  -e "DB_HOST=${DB_HOST:-host.docker.internal}" \
  -e "REDIS_HOST=${REDIS_HOST:-host.docker.internal}" \
  -e "JWT_PUBLIC_KEY_PATH=file:/app/keys/public.pem" \
  "${IMAGE}"

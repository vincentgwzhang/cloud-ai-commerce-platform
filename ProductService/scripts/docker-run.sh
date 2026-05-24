#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${ROOT_DIR}"

IMAGE="${PRODUCT_SERVICE_IMAGE:-product-service:1.0.0}"
CONTAINER_NAME="${PRODUCT_SERVICE_CONTAINER:-product-service}"
HOST_PORT="${HOST_PORT:-8081}"

"${SCRIPT_DIR}/sync-jwt-public-key.sh"

echo "Removing container '${CONTAINER_NAME}' (if present)..."
docker rm -f "${CONTAINER_NAME}" >/dev/null 2>&1 || true

echo "Removing image '${IMAGE}' (if present)..."
docker rmi -f "${IMAGE}" >/dev/null 2>&1 || true

echo "Building JAR..."
mvn -DskipTests package

echo "Building Docker image '${IMAGE}'..."
docker build -t "${IMAGE}" .

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

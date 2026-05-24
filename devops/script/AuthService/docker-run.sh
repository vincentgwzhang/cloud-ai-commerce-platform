#!/usr/bin/env bash
# Build and run AuthService in Docker against host MySQL.
#
# Prerequisite: sudo mysql < devops/db/grant-mysql-docker-access.sql
set -euo pipefail

DEVOPS_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../lib/paths.sh
source "${DEVOPS_SCRIPT_DIR}/../lib/paths.sh"
devops_init_paths "${BASH_SOURCE[0]}"

JWT_KEYS_DIR="${JWT_KEYS_DIR}" "${DEVOPS_ROOT}/script/local-dev-setup.sh" --keys-only

cd "${SERVICE_ROOT}"

IMAGE="${AUTH_SERVICE_IMAGE:-auth-service:1.0.0}"
CONTAINER_NAME="${AUTH_SERVICE_CONTAINER:-auth-service}"
HOST_PORT="${HOST_PORT:-8080}"
DB_HOST="${DB_HOST:-host.docker.internal}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-commerce_platform}"
DB_USERNAME="${DB_USERNAME:-vincent}"
DB_PASSWORD="${DB_PASSWORD:-1q2w3e4R}"

docker rm -f "${CONTAINER_NAME}" >/dev/null 2>&1 || true
docker rmi -f "${IMAGE}" >/dev/null 2>&1 || true

echo "Building JAR (mvn -DskipTests package)..."
mvn -DskipTests package

echo "Building Docker image '${IMAGE}'..."
cd "${REPO_ROOT}"
docker build -f "${SERVICE_ROOT}/Dockerfile" -t "${IMAGE}" .

echo "Starting container '${CONTAINER_NAME}' on port ${HOST_PORT}..."
exec docker run --rm \
  --name "${CONTAINER_NAME}" \
  -p "${HOST_PORT}:8080" \
  --add-host=host.docker.internal:host-gateway \
  -e "DB_HOST=${DB_HOST}" \
  -e "DB_PORT=${DB_PORT}" \
  -e "DB_NAME=${DB_NAME}" \
  -e "DB_USERNAME=${DB_USERNAME}" \
  -e "DB_PASSWORD=${DB_PASSWORD}" \
  -e SPRING_PROFILES_ACTIVE=docker \
  "${IMAGE}"

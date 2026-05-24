#!/usr/bin/env bash
# Build a fresh auth-service image and run it against MySQL on the Ubuntu host.
#
# Each run: remove existing container (if any) → remove image → mvn package → docker build → docker run
#
# Prerequisite: MySQL allows remote/Docker clients — run once on host:
#   sudo mysql < scripts/grant-mysql-docker-access.sql
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${ROOT_DIR}"

IMAGE="${AUTH_SERVICE_IMAGE:-auth-service:1.0.0}"
CONTAINER_NAME="${AUTH_SERVICE_CONTAINER:-auth-service}"
HOST_PORT="${HOST_PORT:-8080}"
DB_HOST="${DB_HOST:-host.docker.internal}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-commerce_platform}"
DB_USERNAME="${DB_USERNAME:-vincent}"
DB_PASSWORD="${DB_PASSWORD:-1q2w3e4R}"

echo "Removing container '${CONTAINER_NAME}' (if present)..."
docker rm -f "${CONTAINER_NAME}" >/dev/null 2>&1 || true

echo "Removing image '${IMAGE}' (if present)..."
docker rmi -f "${IMAGE}" >/dev/null 2>&1 || true

echo "Building JAR (mvn -DskipTests package)..."
mvn -DskipTests package

echo "Building Docker image '${IMAGE}'..."
docker build -t "${IMAGE}" .

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

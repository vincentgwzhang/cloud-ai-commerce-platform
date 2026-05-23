#!/usr/bin/env bash
# Run auth-service container against MySQL on the Ubuntu host (not inside Docker).
#
# Prerequisite: MySQL allows remote/Docker clients — run once on host:
#   sudo mysql < scripts/grant-mysql-docker-access.sql
set -euo pipefail

IMAGE="${AUTH_SERVICE_IMAGE:-auth-service:1.0}"
HOST_PORT="${HOST_PORT:-8080}"
DB_HOST="${DB_HOST:-host.docker.internal}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-commerce_platform}"
DB_USERNAME="${DB_USERNAME:-vincent}"
DB_PASSWORD="${DB_PASSWORD:-1q2w3e4R}"

exec docker run --rm \
  -p "${HOST_PORT}:8080" \
  --add-host=host.docker.internal:host-gateway \
  -e "DB_HOST=${DB_HOST}" \
  -e "DB_PORT=${DB_PORT}" \
  -e "DB_NAME=${DB_NAME}" \
  -e "DB_USERNAME=${DB_USERNAME}" \
  -e "DB_PASSWORD=${DB_PASSWORD}" \
  "${IMAGE}"

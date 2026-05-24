#!/usr/bin/env bash
# Copy Auth Service public key into ProductService/data/keys (mainly for Docker profile).
# IntelliJ local profile reads ../AuthService/data/keys/public.pem directly — sync optional.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
AUTH_KEYS="$(cd "${ROOT_DIR}/../AuthService" && pwd)/data/keys"
DEST="${ROOT_DIR}/data/keys"

mkdir -p "${DEST}"
if [[ ! -f "${AUTH_KEYS}/public.pem" ]]; then
  echo "Missing ${AUTH_KEYS}/public.pem — run AuthService/scripts/generate-rsa-keys.sh first." >&2
  exit 1
fi
cp "${AUTH_KEYS}/public.pem" "${DEST}/public.pem"
echo "Copied public.pem to ${DEST}/"

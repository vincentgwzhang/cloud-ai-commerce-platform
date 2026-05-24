#!/usr/bin/env bash
# Export the public key from the running Minikube auth-service-jwt-keys Secret.
# Use when ProductService runs locally but login goes through Minikube AuthService.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEST="${SCRIPT_DIR}/../data/keys"
mkdir -p "${DEST}"

if ! kubectl get secret auth-service-jwt-keys >/dev/null 2>&1; then
  echo "ERROR: Secret auth-service-jwt-keys not found. Deploy AuthService to Minikube first." >&2
  exit 1
fi

kubectl get secret auth-service-jwt-keys -o jsonpath='{.data.public\.pem}' | base64 -d > "${DEST}/public.pem"
echo "Wrote ${DEST}/public.pem from Minikube secret auth-service-jwt-keys"
echo "Restart ProductService and login again via Minikube Auth URL to get a fresh token."

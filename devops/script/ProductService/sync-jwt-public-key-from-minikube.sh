#!/usr/bin/env bash
# Export JWT public key from Minikube auth-service-jwt-keys Secret into devops/data/keys.
set -euo pipefail

DEVOPS_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../lib/paths.sh
source "${DEVOPS_SCRIPT_DIR}/../lib/paths.sh"
devops_init_paths "${BASH_SOURCE[0]}"

mkdir -p "${JWT_KEYS_DIR}"

if ! kubectl get secret auth-service-jwt-keys >/dev/null 2>&1; then
  echo "ERROR: Secret auth-service-jwt-keys not found. Run devops/script/install.sh first." >&2
  exit 1
fi

kubectl get secret auth-service-jwt-keys -o jsonpath='{.data.public\.pem}' | base64 -d > "${JWT_KEYS_DIR}/public.pem"
chmod 644 "${JWT_KEYS_DIR}/public.pem"
echo "Wrote ${JWT_KEYS_DIR}/public.pem from Minikube secret auth-service-jwt-keys"

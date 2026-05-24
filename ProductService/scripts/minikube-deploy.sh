#!/usr/bin/env bash
# Build image inside Minikube's Docker, apply ProductService manifests.
# Runs minikube-uninstall.sh first (clean slate), then deploys fresh.
# Reuses AuthService secrets: auth-service-secret, auth-service-jwt-keys.
#
# Prerequisite: AuthService on Minikube (../AuthService/scripts/minikube-deploy.sh)
# Host MySQL + Redis (OS on Ubuntu) via host.minikube.internal — see ../../scripts/minikube-host-services.md
#
# Usage (from ProductService/): ./scripts/minikube-deploy.sh
# Teardown only:                 ./scripts/minikube-uninstall.sh
#
# Optional:
#   PRODUCT_SERVICE_IMAGE=product-service:1.0.0
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
# shellcheck source=minikube-lib.sh
source "${ROOT}/scripts/minikube-lib.sh"

IMAGE="${PRODUCT_SERVICE_IMAGE:-${PRODUCT_SERVICE_DEFAULT_IMAGE}}"

if ! minikube status >/dev/null 2>&1; then
  echo "ERROR: minikube is not running. Start with: minikube start" >&2
  exit 1
fi

echo "==> Phase 1: clean slate (minikube-uninstall.sh)"
MINIKUBE_DEPLOY=1 "${ROOT}/scripts/minikube-uninstall.sh"

require_auth_service_secrets

ensure_jwt_public_key
if [[ ! -f ./data/keys/public.pem ]]; then
  echo "ERROR: data/keys/public.pem missing after sync" >&2
  exit 1
fi

echo "==> Phase 2: build image in Minikube Docker"
eval "$(minikube docker-env)"

echo "==> mvn clean install"
mvn clean install

echo "==> Building Docker image: ${IMAGE}"
docker build -t "${IMAGE}" .

echo "==> Restoring host Docker context"
eval "$(minikube docker-env -u)"

if [[ "${SKIP_HOST_REDIS_CHECK:-0}" != "1" ]]; then
  echo "==> Preflight: host Redis reachable from Minikube"
  chmod +x "${ROOT}/scripts/check-host-redis-minikube.sh"
  "${ROOT}/scripts/check-host-redis-minikube.sh"
fi

echo "==> Phase 3: apply manifests"
kubectl apply -f k8s/minikube/configmap-host-mysql.yaml
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml

echo "==> Rollout status"
kubectl rollout status deployment/product-service --timeout=180s

echo "==> Done. Get URL:"
echo "    minikube service product-service --url"
echo "    # or: kubectl port-forward svc/product-service 8081:80"
echo ""
echo "Login via AuthService, then call product API with the same Bearer token:"
echo "    minikube service auth-service --url"

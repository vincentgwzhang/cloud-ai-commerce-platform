#!/usr/bin/env bash
# Build ProductService image in Minikube and apply devops/k8s/ProductService manifests.
#
# Prerequisite: AuthService secrets on cluster (install.sh or AuthService deploy).
# Host MySQL + Redis: devops/docs/minikube-host-services.md
#
# Skips inner uninstall when MINIKUBE_SKIP_UNINSTALL=1 (devops/script/install.sh).
set -euo pipefail

DEVOPS_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../lib/paths.sh
source "${DEVOPS_SCRIPT_DIR}/../lib/paths.sh"
devops_init_paths "${BASH_SOURCE[0]}"
# shellcheck source=minikube-lib.sh
source "${DEVOPS_SCRIPT_SERVICE_DIR}/minikube-lib.sh"

IMAGE="${PRODUCT_SERVICE_IMAGE:-${PRODUCT_SERVICE_DEFAULT_IMAGE}}"

if ! minikube status >/dev/null 2>&1; then
  echo "ERROR: minikube is not running. Start with: minikube start" >&2
  exit 1
fi

if [[ "${MINIKUBE_SKIP_UNINSTALL:-0}" != "1" ]]; then
  echo "==> Phase 1: clean slate (minikube-uninstall.sh)"
  MINIKUBE_DEPLOY=1 "${DEVOPS_SCRIPT_SERVICE_DIR}/minikube-uninstall.sh"
fi

require_auth_service_secrets

ensure_jwt_public_key

echo "==> Phase 2: build image in Minikube Docker (${SERVICE_ROOT})"
cd "${SERVICE_ROOT}"
eval "$(minikube docker-env)"

echo "==> mvn clean install"
mvn clean install

echo "==> Building Docker image: ${IMAGE} (context: repo root)"
cd "${REPO_ROOT}"
docker build -f "${SERVICE_ROOT}/Dockerfile" -t "${IMAGE}" .

eval "$(minikube docker-env -u)"

if [[ "${SKIP_HOST_REDIS_CHECK:-0}" != "1" ]]; then
  echo "==> Preflight: host Redis reachable from Minikube"
  chmod +x "${DEVOPS_SCRIPT_SERVICE_DIR}/check-host-redis-minikube.sh"
  "${DEVOPS_SCRIPT_SERVICE_DIR}/check-host-redis-minikube.sh"
fi

echo "==> Phase 3: apply manifests"
kubectl apply -f "${K8S_DIR}/minikube/configmap-host-mysql.yaml"
kubectl apply -f "${K8S_DIR}/deployment.yaml"
kubectl apply -f "${K8S_DIR}/service.yaml"

echo "==> Rollout status"
kubectl rollout status deployment/product-service --timeout=180s

echo "==> ProductService done. Get URL:"
echo "    minikube service product-service --url"
echo "    kubectl port-forward svc/product-service 8081:80"

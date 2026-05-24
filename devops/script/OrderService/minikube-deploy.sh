#!/usr/bin/env bash
set -euo pipefail

DEVOPS_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../lib/paths.sh
source "${DEVOPS_SCRIPT_DIR}/../lib/paths.sh"
devops_init_paths "${BASH_SOURCE[0]}"
# shellcheck source=minikube-lib.sh
source "${DEVOPS_SCRIPT_SERVICE_DIR}/minikube-lib.sh"

IMAGE="${ORDER_SERVICE_IMAGE:-${ORDER_SERVICE_DEFAULT_IMAGE}}"

if ! minikube status >/dev/null 2>&1; then
  echo "ERROR: minikube is not running." >&2
  exit 1
fi

if [[ "${MINIKUBE_SKIP_UNINSTALL:-0}" != "1" ]]; then
  MINIKUBE_DEPLOY=1 "${DEVOPS_SCRIPT_SERVICE_DIR}/minikube-uninstall.sh"
fi

require_auth_service_secrets
ensure_jwt_public_key

if [[ "${SKIP_KAFKA_CHECK:-0}" != "1" ]]; then
  chmod +x "${DEVOPS_ROOT}/script/lib/check-host-kafka.sh"
  "${DEVOPS_ROOT}/script/lib/check-host-kafka.sh"
fi

if [[ "${SKIP_HOST_REDIS_CHECK:-0}" != "1" ]]; then
  "${DEVOPS_ROOT}/script/ProductService/check-host-redis-minikube.sh"
fi

echo "==> Phase 2: build (${SERVICE_ROOT})"
cd "${SERVICE_ROOT}"
eval "$(minikube docker-env)"
mvn clean install
cd "${REPO_ROOT}"
docker build -f "${SERVICE_ROOT}/Dockerfile" -t "${IMAGE}" .
eval "$(minikube docker-env -u)"

echo "==> Phase 3: apply manifests"
kubectl apply -f "${K8S_DIR}/minikube/configmap-host-mysql.yaml"
kubectl apply -f "${K8S_DIR}/deployment.yaml"
kubectl apply -f "${K8S_DIR}/service.yaml"

kubectl rollout status deployment/order-service --timeout=180s

echo "==> OrderService done"
echo "    minikube service order-service --url"
echo "    kubectl port-forward svc/order-service 8083:80"
echo "    Kafka UI: http://localhost:18080"

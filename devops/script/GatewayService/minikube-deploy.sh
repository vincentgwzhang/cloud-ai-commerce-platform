#!/usr/bin/env bash
set -euo pipefail

DEVOPS_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../lib/paths.sh
source "${DEVOPS_SCRIPT_DIR}/../lib/paths.sh"
devops_init_paths "${BASH_SOURCE[0]}"
# shellcheck source=minikube-lib.sh
source "${DEVOPS_SCRIPT_SERVICE_DIR}/minikube-lib.sh"

IMAGE="${GATEWAY_SERVICE_IMAGE:-${GATEWAY_SERVICE_DEFAULT_IMAGE}}"

if ! minikube status >/dev/null 2>&1; then
  echo "ERROR: minikube is not running." >&2
  exit 1
fi

if [[ "${MINIKUBE_SKIP_UNINSTALL:-0}" != "1" ]]; then
  MINIKUBE_DEPLOY=1 "${DEVOPS_SCRIPT_SERVICE_DIR}/minikube-uninstall.sh"
fi

require_auth_service_secrets
require_backend_services
ensure_jwt_public_key

echo "==> Phase 2: build (${SERVICE_ROOT})"
cd "${SERVICE_ROOT}"
eval "$(minikube docker-env)"
mvn clean package -DskipTests
cd "${REPO_ROOT}"
docker build -f "${SERVICE_ROOT}/Dockerfile" -t "${IMAGE}" .
eval "$(minikube docker-env -u)"

echo "==> Phase 3: apply manifests"
kubectl apply -f "${K8S_DIR}/minikube/configmap-host-mysql.yaml"
kubectl apply -f "${K8S_DIR}/deployment.yaml"
kubectl apply -f "${K8S_DIR}/service.yaml"

kubectl rollout status deployment/gateway-service --timeout=180s

echo "==> GatewayService done"
echo "    minikube service gateway-service --url"
echo "    kubectl port-forward svc/gateway-service 8088:80"

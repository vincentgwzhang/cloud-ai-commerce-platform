#!/usr/bin/env bash
# Build AuthService image in Minikube Docker and apply manifests from devops/k8s/AuthService.
#
# Usage: devops/script/AuthService/minikube-deploy.sh
# Skips inner uninstall when MINIKUBE_SKIP_UNINSTALL=1 (used by devops/script/install.sh).
#
# Optional:
#   DB_PASSWORD=secret
#   AUTH_SERVICE_IMAGE=auth-service:1.0.0
#   FORCE_RSA_REGENERATE=1
set -euo pipefail

DEVOPS_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../lib/paths.sh
source "${DEVOPS_SCRIPT_DIR}/../lib/paths.sh"
devops_init_paths "${BASH_SOURCE[0]}"
# shellcheck source=minikube-lib.sh
source "${DEVOPS_SCRIPT_SERVICE_DIR}/minikube-lib.sh"

IMAGE="${AUTH_SERVICE_IMAGE:-${AUTH_SERVICE_DEFAULT_IMAGE}}"
DB_PASSWORD="${DB_PASSWORD:-1q2w3e4R}"
FORCE_RSA_REGENERATE="${FORCE_RSA_REGENERATE:-0}"

if ! minikube status >/dev/null 2>&1; then
  echo "ERROR: minikube is not running. Start with: minikube start" >&2
  exit 1
fi

if [[ "${MINIKUBE_SKIP_UNINSTALL:-0}" != "1" ]]; then
  echo "==> Phase 1: clean slate (minikube-uninstall.sh)"
  MINIKUBE_DEPLOY=1 "${DEVOPS_SCRIPT_SERVICE_DIR}/minikube-uninstall.sh"
fi

if [[ "${FORCE_RSA_REGENERATE}" == "1" ]]; then
  remove_local_rsa_keys
fi
ensure_rsa_keys

if [[ ! -f "${JWT_KEYS_DIR}/private.pem" || ! -f "${JWT_KEYS_DIR}/public.pem" ]]; then
  echo "ERROR: RSA keys missing in ${JWT_KEYS_DIR}" >&2
  exit 1
fi

echo "==> Phase 2: build image in Minikube Docker (${SERVICE_ROOT})"
cd "${SERVICE_ROOT}"
eval "$(minikube docker-env)"

echo "==> mvn clean package -DskipTests"
mvn clean package -DskipTests

echo "==> Building Docker image: ${IMAGE} (context: repo root)"
cd "${REPO_ROOT}"
docker build -f "${SERVICE_ROOT}/Dockerfile" -t "${IMAGE}" .

echo "==> Restoring host Docker context"
eval "$(minikube docker-env -u)"

echo "==> Phase 3: apply secrets + manifests"
kubectl create secret generic auth-service-jwt-keys \
  --from-file=private.pem="${JWT_KEYS_DIR}/private.pem" \
  --from-file=public.pem="${JWT_KEYS_DIR}/public.pem" \
  --dry-run=client -o yaml | kubectl apply -f -

kubectl create secret generic auth-service-secret \
  --from-literal=DB_USERNAME=vincent \
  --from-literal="DB_PASSWORD=${DB_PASSWORD}" \
  --dry-run=client -o yaml | kubectl apply -f -

echo "==> Applying ConfigMap + Deployment + Service"
kubectl apply -f "${K8S_DIR}/minikube/configmap-host-mysql.yaml"
kubectl apply -f "${K8S_DIR}/deployment.yaml"
kubectl apply -f "${K8S_DIR}/service.yaml"

echo "==> Rollout status"
kubectl rollout status deployment/auth-service --timeout=120s

echo "==> AuthService done. Get URL:"
echo "    minikube service auth-service --url"
echo "    kubectl port-forward svc/auth-service 8080:80"

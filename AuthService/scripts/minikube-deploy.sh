#!/usr/bin/env bash
# Build image inside Minikube's Docker, create secrets, apply k8s manifests.
# Runs minikube-uninstall.sh first (clean slate), then deploys fresh.
# Usage (from AuthService/): ./scripts/minikube-deploy.sh
# Teardown only:             ./scripts/minikube-uninstall.sh
#
# Optional:
#   DB_PASSWORD=secret
#   AUTH_SERVICE_IMAGE=auth-service:1.0.0
#   FORCE_RSA_REGENERATE=1   delete local keys then run generate-rsa-keys.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
# shellcheck source=minikube-lib.sh
source "${ROOT}/scripts/minikube-lib.sh"

IMAGE="${AUTH_SERVICE_IMAGE:-${AUTH_SERVICE_DEFAULT_IMAGE}}"
DB_PASSWORD="${DB_PASSWORD:-1q2w3e4R}"
FORCE_RSA_REGENERATE="${FORCE_RSA_REGENERATE:-0}"

if ! minikube status >/dev/null 2>&1; then
  echo "ERROR: minikube is not running. Start with: minikube start" >&2
  exit 1
fi

echo "==> Phase 1: clean slate (minikube-uninstall.sh)"
MINIKUBE_DEPLOY=1 "${ROOT}/scripts/minikube-uninstall.sh"

if [[ "${FORCE_RSA_REGENERATE}" == "1" ]]; then
  remove_local_rsa_keys
fi
ensure_rsa_keys

if [[ ! -f ./data/keys/private.pem || ! -f ./data/keys/public.pem ]]; then
  echo "ERROR: RSA keys missing after generate-rsa-keys.sh" >&2
  exit 1
fi

echo "==> Phase 2: build image in Minikube Docker"
eval "$(minikube docker-env)"

echo "==> Building JAR + Docker image: ${IMAGE}"
mvn -q -DskipTests package
docker build -t "${IMAGE}" .

echo "==> Restoring host Docker context"
eval "$(minikube docker-env -u)"

echo "==> Phase 3: apply secrets + manifests"
kubectl create secret generic auth-service-jwt-keys \
  --from-file=private.pem=./data/keys/private.pem \
  --from-file=public.pem=./data/keys/public.pem \
  --dry-run=client -o yaml | kubectl apply -f -

kubectl create secret generic auth-service-secret \
  --from-literal=DB_USERNAME=vincent \
  --from-literal="DB_PASSWORD=${DB_PASSWORD}" \
  --dry-run=client -o yaml | kubectl apply -f -

echo "==> Applying ConfigMap (host.minikube.internal) + Deployment + Service"
kubectl apply -f k8s/minikube/configmap-host-mysql.yaml
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml

echo "==> Rollout status"
kubectl rollout status deployment/auth-service --timeout=120s

echo "==> Done. Get URL:"
echo "    minikube service auth-service --url"
echo "    # or: kubectl port-forward svc/auth-service 8080:80"

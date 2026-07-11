#!/usr/bin/env bash
# Deploy all 5 microservices to Minikube via Helm (K8s only).
#
# Does NOT start/stop MySQL, Redis, or Kafka — you run those on the host yourself.
# Pods connect via host.minikube.internal (see commerce-platform/values.yaml).
#
# Usage:
#   devops/helm/helm-install.sh
#
# Prerequisites (your responsibility):
#   minikube start
#   MySQL on host :3306, Redis :6379, Kafka :9092 (e.g. 127.0.0.1)
#
# Optional env:
#   DB_PASSWORD=1q2w3e4R
#   OPENAI_API_KEY=sk-...
#   HELM_RELEASE=commerce-platform
#   HELM_NAMESPACE=default
#   SKIP_BUILD=1
#   HELM_SKIP_UNINSTALL=1
set -euo pipefail

HELM_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEVOPS_ROOT="$(cd "${HELM_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${DEVOPS_ROOT}/.." && pwd)"
CHART_DIR="${HELM_DIR}/commerce-platform"
DIST_DIR="${HELM_DIR}/dist"
LOCAL_DEV_SETUP="${DEVOPS_ROOT}/script/local-dev-setup.sh"
JWT_KEYS_DIR="${DEVOPS_ROOT}/data/keys"
ARGOCD_DIR="${DEVOPS_ROOT}/argocd"
SEALED_VALUES="${CHART_DIR}/values-sealed.yaml"

HELM_RELEASE="${HELM_RELEASE:-commerce-platform}"
HELM_NAMESPACE="${HELM_NAMESPACE:-default}"
DB_PASSWORD="${DB_PASSWORD:-1q2w3e4R}"
DB_USERNAME="${DB_USERNAME:-vincent}"
SKIP_BUILD="${SKIP_BUILD:-0}"

AUTH_IMAGE="${AUTH_SERVICE_IMAGE:-auth-service:1.0.0}"
PRODUCT_IMAGE="${PRODUCT_SERVICE_IMAGE:-product-service:1.0.0}"
INVENTORY_IMAGE="${INVENTORY_SERVICE_IMAGE:-inventory-service:1.0.0}"
ORDER_IMAGE="${ORDER_SERVICE_IMAGE:-order-service:1.0.0}"
GATEWAY_IMAGE="${GATEWAY_SERVICE_IMAGE:-gateway-service:1.0.0}"
AI_IMAGE="${AI_SERVICE_IMAGE:-ai-service:1.0.0}"
OPENAI_API_KEY="${OPENAI_API_KEY:-}"

# All platform microservices (Maven modules at repo root).
PLATFORM_SERVICES=(
  "AuthService:${AUTH_IMAGE}"
  "ProductService:${PRODUCT_IMAGE}"
  "InventoryService:${INVENTORY_IMAGE}"
  "OrderService:${ORDER_IMAGE}"
  "GatewayService:${GATEWAY_IMAGE}"
  "AiService:${AI_IMAGE}"
)

if ! minikube status >/dev/null 2>&1; then
  echo "ERROR: minikube is not running. Start with: minikube start" >&2
  exit 1
fi

if ! command -v helm >/dev/null 2>&1; then
  echo "ERROR: helm not found" >&2
  exit 1
fi

if [[ ! -f "${CHART_DIR}/Chart.yaml" ]]; then
  echo "ERROR: chart missing at ${CHART_DIR}" >&2
  exit 1
fi

if [[ -z "${OPENAI_API_KEY}" ]]; then
  echo "ERROR: OPENAI_API_KEY is required." >&2
  echo "Run: OPENAI_API_KEY=sk-... ${HELM_DIR}/helm-install.sh" >&2
  exit 1
fi

echo "========================================"
echo "  Helm install (commerce-platform)"
echo "========================================"
echo ""
echo "  Host deps (not managed by this script):"
echo "    MySQL  ${DB_USERNAME}@host.minikube.internal:3306"
echo "    Redis  host.minikube.internal:6379"
echo "    Kafka  host.minikube.internal:9092"
echo "    Chroma host.minikube.internal:8000 (ai-service RAG vector store)"
echo "  Ensure they are already running on your machine."

if [[ "${HELM_SKIP_UNINSTALL:-0}" != "1" ]]; then
  echo ""
  echo "==> Step 0: uninstall existing K8s workloads"
  "${HELM_DIR}/helm-uninstall.sh"
fi

echo ""
echo "==> Step 1: install Argo CD + Sealed Secrets"
"${ARGOCD_DIR}/install-argocd.sh"
"${ARGOCD_DIR}/sealed-secrets/install-sealed-secrets.sh"

echo ""
echo "==> Step 2: JWT keys (${JWT_KEYS_DIR})"
chmod +x "${LOCAL_DEV_SETUP}"
JWT_KEYS_DIR="${JWT_KEYS_DIR}" "${LOCAL_DEV_SETUP}" --keys-only

if [[ ! -f "${JWT_KEYS_DIR}/private.pem" || ! -f "${JWT_KEYS_DIR}/public.pem" ]]; then
  echo "ERROR: RSA keys missing in ${JWT_KEYS_DIR}" >&2
  exit 1
fi

echo ""
echo "==> Step 3: Kubernetes secrets"
kubectl create secret generic auth-service-jwt-keys \
  --namespace "${HELM_NAMESPACE}" \
  --from-file=private.pem="${JWT_KEYS_DIR}/private.pem" \
  --from-file=public.pem="${JWT_KEYS_DIR}/public.pem" \
  --dry-run=client -o yaml | kubectl apply -f -

kubectl create secret generic auth-service-secret \
  --namespace "${HELM_NAMESPACE}" \
  --from-literal=DB_USERNAME="${DB_USERNAME}" \
  --from-literal="DB_PASSWORD=${DB_PASSWORD}" \
  --dry-run=client -o yaml | kubectl apply -f -

# ai-service OpenAI key (optional secret; ai-service boots without it but RAG/chat calls fail).
if [[ -n "${OPENAI_API_KEY}" ]]; then
  echo "==> Sealing ai-service OPENAI_API_KEY for Argo CD / Sealed Secrets"
  SECRET_NAMESPACE="${HELM_NAMESPACE}" "${ARGOCD_DIR}/sealed-secrets/seal-ai-openai-key.sh" "${OPENAI_API_KEY}"
  kubectl delete secret ai-service-secret --namespace "${HELM_NAMESPACE}" --ignore-not-found

  # Do not create ai-service-secret directly here. It is created by the Sealed Secrets
  # controller from the Helm-rendered SealedSecret.
  # kubectl create secret generic ai-service-secret \
  #   --namespace "${HELM_NAMESPACE}" \
  #   --from-literal="OPENAI_API_KEY=${OPENAI_API_KEY}" \
  #   --dry-run=client -o yaml | kubectl apply -f -
else
  echo "    NOTE: OPENAI_API_KEY not set — skipping ai-service-secret."
  echo "          ai-service will start, but OpenAI embedding/chat calls fail until you create it:"
  echo "          OPENAI_API_KEY=sk-... ${HELM_DIR}/helm-install.sh"
fi

split_image() {
  local image="$1"
  local repo="${image%%:*}"
  local tag="${image#*:}"
  if [[ "${repo}" == "${tag}" ]]; then
    tag="latest"
  fi
  printf '%s %s' "${repo}" "${tag}"
}

build_and_tag() {
  local service_name="$1"
  local image="$2"
  local service_root="${REPO_ROOT}/${service_name}"

  if [[ ! -f "${service_root}/pom.xml" ]]; then
    echo "ERROR: ${service_root}/pom.xml not found" >&2
    exit 1
  fi

  echo "==> Building ${service_name} -> ${image}"
  cd "${service_root}"
  mvn clean package -DskipTests -q
  cd "${REPO_ROOT}"
  docker build -f "${service_root}/Dockerfile" -t "${image}" .
}

if [[ "${SKIP_BUILD}" != "1" ]]; then
  echo ""
  echo "==> Step 4: build all platform images in Minikube Docker"
  eval "$(minikube docker-env)"
  for entry in "${PLATFORM_SERVICES[@]}"; do
    svc="${entry%%:*}"
    img="${entry#*:}"
    build_and_tag "${svc}" "${img}"
  done
  eval "$(minikube docker-env -u)"
else
  echo ""
  echo "==> Step 4: SKIP_BUILD=1 — using existing images in Minikube"
fi

read -r AUTH_REPO AUTH_TAG <<< "$(split_image "${AUTH_IMAGE}")"
read -r PRODUCT_REPO PRODUCT_TAG <<< "$(split_image "${PRODUCT_IMAGE}")"
read -r INVENTORY_REPO INVENTORY_TAG <<< "$(split_image "${INVENTORY_IMAGE}")"
read -r ORDER_REPO ORDER_TAG <<< "$(split_image "${ORDER_IMAGE}")"
read -r GATEWAY_REPO GATEWAY_TAG <<< "$(split_image "${GATEWAY_IMAGE}")"
read -r AI_REPO AI_TAG <<< "$(split_image "${AI_IMAGE}")"

CHART_REF="${CHART_DIR}"
HELM_VALUE_ARGS=()
if [[ -f "${SEALED_VALUES}" ]]; then
  HELM_VALUE_ARGS=(-f "${SEALED_VALUES}")
fi
if [[ -d "${DIST_DIR}" ]]; then
  LATEST_PKG="$(ls -1t "${DIST_DIR}"/commerce-platform-*.tgz 2>/dev/null | head -1 || true)"
  if [[ -n "${LATEST_PKG}" ]]; then
    CHART_REF="${LATEST_PKG}"
    echo ""
    echo "==> Using packaged chart: ${CHART_REF}"
  fi
fi

echo ""
echo "==> Step 5: helm upgrade --install"
helm upgrade --install "${HELM_RELEASE}" "${CHART_REF}" \
  "${HELM_VALUE_ARGS[@]}" \
  --namespace "${HELM_NAMESPACE}" \
  --create-namespace \
  --set "services.auth.image.repository=${AUTH_REPO}" \
  --set "services.auth.image.tag=${AUTH_TAG}" \
  --set "services.product.image.repository=${PRODUCT_REPO}" \
  --set "services.product.image.tag=${PRODUCT_TAG}" \
  --set "services.inventory.image.repository=${INVENTORY_REPO}" \
  --set "services.inventory.image.tag=${INVENTORY_TAG}" \
  --set "services.order.image.repository=${ORDER_REPO}" \
  --set "services.order.image.tag=${ORDER_TAG}" \
  --set "services.gateway.image.repository=${GATEWAY_REPO}" \
  --set "services.gateway.image.tag=${GATEWAY_TAG}" \
  --set "services.ai.image.repository=${AI_REPO}" \
  --set "services.ai.image.tag=${AI_TAG}" \
  --wait \
  --timeout 10m

echo ""
echo "==> Step 6: rollout status (auth first, then business services, gateway last)"
for dep in auth-service product-service inventory-service order-service ai-service gateway-service; do
  kubectl rollout status "deployment/${dep}" -n "${HELM_NAMESPACE}" --timeout=300s
done

echo ""
echo "========================================"
echo "  Helm install complete"
echo "========================================"
helm status "${HELM_RELEASE}" -n "${HELM_NAMESPACE}" 2>/dev/null || true
echo ""
echo "  Host deps (you manage): MySQL / Redis / Kafka on host.minikube.internal"
echo ""
echo "  Auth:     kubectl port-forward -n ${HELM_NAMESPACE} svc/auth-service 8080:80"
echo "  Gateway:  kubectl port-forward -n ${HELM_NAMESPACE} svc/gateway-service 8088:80"
echo "  Product:  kubectl port-forward -n ${HELM_NAMESPACE} svc/product-service 8081:80"
echo "  Inventory:kubectl port-forward -n ${HELM_NAMESPACE} svc/inventory-service 8082:80"
echo "  Order:    kubectl port-forward -n ${HELM_NAMESPACE} svc/order-service 8083:80"
echo "  AI:       kubectl port-forward -n ${HELM_NAMESPACE} svc/ai-service 8084:80"
echo ""
echo "  Teardown: ${HELM_DIR}/helm-uninstall.sh"

#!/usr/bin/env bash

readonly GATEWAY_SERVICE_DEFAULT_IMAGE="${GATEWAY_SERVICE_DEFAULT_IMAGE:-gateway-service:1.0.0}"

gateway_service_image_tags() {
  local primary="${GATEWAY_SERVICE_IMAGE:-${GATEWAY_SERVICE_DEFAULT_IMAGE}}"
  printf '%s\n' "${primary}" "gateway-service:1.0" "gateway-service:1.0.0" | awk '!seen[$0]++'
}

ensure_jwt_public_key() {
  if [[ ! -f "${JWT_KEYS_DIR}/public.pem" ]]; then
    echo "ERROR: ${JWT_KEYS_DIR}/public.pem missing. Run: devops/script/local-dev-setup.sh" >&2
    return 1
  fi
}

remove_gateway_service_minikube_images() {
  echo "==> Removing gateway-service images from Minikube image store (best effort)"
  while IFS= read -r tag; do
    [[ -z "${tag}" ]] && continue
    minikube image rm "${tag}" 2>/dev/null || true
  done < <(gateway_service_image_tags)

  if ! eval "$(minikube docker-env)" 2>/dev/null; then
    return 0
  fi

  while IFS= read -r ref; do
    [[ -z "${ref}" || "${ref}" == *"<none>"* ]] && continue
    docker rmi -f "${ref}" 2>/dev/null || true
  done < <(docker images gateway-service --format '{{.Repository}}:{{.Tag}}' 2>/dev/null \
    | grep -v '<none>' || true)

  eval "$(minikube docker-env -u)" 2>/dev/null || true
}

require_auth_service_secrets() {
  local missing=0
  for name in auth-service-jwt-keys auth-service-secret; do
    if ! kubectl get secret "${name}" >/dev/null 2>&1; then
      echo "ERROR: Secret ${name} not found. Deploy AuthService first." >&2
      missing=1
    fi
  done
  [[ "${missing}" -eq 0 ]]
}

require_backend_services() {
  local missing=0
  for svc in product-service inventory-service order-service; do
    if ! kubectl get service "${svc}" >/dev/null 2>&1; then
      echo "ERROR: Service ${svc} not found. Deploy Product, Inventory, and Order services first." >&2
      missing=1
    fi
  done
  [[ "${missing}" -eq 0 ]]
}

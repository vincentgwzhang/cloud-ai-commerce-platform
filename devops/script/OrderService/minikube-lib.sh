#!/usr/bin/env bash

readonly ORDER_SERVICE_DEFAULT_IMAGE="${ORDER_SERVICE_DEFAULT_IMAGE:-order-service:1.0.0}"

order_service_image_tags() {
  local primary="${ORDER_SERVICE_IMAGE:-${ORDER_SERVICE_DEFAULT_IMAGE}}"
  printf '%s\n' "${primary}" "order-service:1.0" "order-service:1.0.0" | awk '!seen[$0]++'
}

ensure_jwt_public_key() {
  if [[ ! -f "${JWT_KEYS_DIR}/public.pem" ]]; then
    echo "ERROR: ${JWT_KEYS_DIR}/public.pem missing. Run: devops/script/local-dev-setup.sh" >&2
    return 1
  fi
}

remove_order_service_minikube_images() {
  echo "==> Removing order-service images from Minikube image store (best effort)"
  while IFS= read -r tag; do
    [[ -z "${tag}" ]] && continue
    minikube image rm "${tag}" 2>/dev/null || true
  done < <(order_service_image_tags)

  if ! eval "$(minikube docker-env)" 2>/dev/null; then
    return 0
  fi

  while IFS= read -r ref; do
    [[ -z "${ref}" || "${ref}" == *"<none>"* ]] && continue
    docker rmi -f "${ref}" 2>/dev/null || true
  done < <(docker images order-service --format '{{.Repository}}:{{.Tag}}' 2>/dev/null \
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

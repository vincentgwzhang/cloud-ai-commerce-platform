#!/usr/bin/env bash
# Shared helpers for AuthService Minikube deploy/uninstall.

readonly AUTH_SERVICE_DEFAULT_IMAGE="${AUTH_SERVICE_DEFAULT_IMAGE:-auth-service:1.0.0}"

auth_service_image_tags() {
  local primary="${AUTH_SERVICE_IMAGE:-${AUTH_SERVICE_DEFAULT_IMAGE}}"
  printf '%s\n' "${primary}" "auth-service:1.0" "auth-service:1.0.0" | awk '!seen[$0]++'
}

ensure_rsa_keys() {
  echo "==> Ensuring RSA keys in ${JWT_KEYS_DIR}"
  JWT_KEYS_DIR="${JWT_KEYS_DIR}" "${DEVOPS_ROOT}/script/local-dev-setup.sh" --keys-only
}

remove_auth_service_minikube_images() {
  echo "==> Removing auth-service images from Minikube image store (best effort)"
  while IFS= read -r tag; do
    [[ -z "${tag}" ]] && continue
    minikube image rm "${tag}" 2>/dev/null || true
  done < <(auth_service_image_tags)

  if ! eval "$(minikube docker-env)" 2>/dev/null; then
    return 0
  fi

  while IFS= read -r ref; do
    [[ -z "${ref}" || "${ref}" == *"<none>"* ]] && continue
    docker rmi -f "${ref}" 2>/dev/null || true
  done < <(docker images auth-service --format '{{.Repository}}:{{.Tag}}' 2>/dev/null \
    | grep -v '<none>' || true)

  eval "$(minikube docker-env -u)" 2>/dev/null || true
}

remove_local_rsa_keys() {
  echo "==> Removing local RSA keys in ${JWT_KEYS_DIR}"
  rm -f "${JWT_KEYS_DIR}/private.pem" "${JWT_KEYS_DIR}/public.pem"
}

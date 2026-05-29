#!/usr/bin/env bash

readonly AI_SERVICE_DEFAULT_IMAGE="${AI_SERVICE_DEFAULT_IMAGE:-ai-service:1.0.0}"

ai_service_image_tags() {
  local primary="${AI_SERVICE_IMAGE:-${AI_SERVICE_DEFAULT_IMAGE}}"
  printf '%s\n' "${primary}" "ai-service:1.0" "ai-service:1.0.0" | awk '!seen[$0]++'
}

ensure_jwt_public_key() {
  if [[ ! -f "${JWT_KEYS_DIR}/public.pem" ]]; then
    echo "ERROR: ${JWT_KEYS_DIR}/public.pem missing. Run: devops/script/local-dev-setup.sh" >&2
    return 1
  fi
}

remove_ai_service_minikube_images() {
  echo "==> Removing ai-service images from Minikube image store (best effort)"
  while IFS= read -r tag; do
    [[ -z "${tag}" ]] && continue
    minikube image rm "${tag}" 2>/dev/null || true
  done < <(ai_service_image_tags)

  if ! eval "$(minikube docker-env)" 2>/dev/null; then
    return 0
  fi

  while IFS= read -r ref; do
    [[ -z "${ref}" || "${ref}" == *"<none>"* ]] && continue
    docker rmi -f "${ref}" 2>/dev/null || true
  done < <(docker images ai-service --format '{{.Repository}}:{{.Tag}}' 2>/dev/null \
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

# Optional OpenAI key secret. ai-service boots without it, but RAG/chat OpenAI calls fail.
ensure_ai_service_secret() {
  if [[ -n "${OPENAI_API_KEY:-}" ]]; then
    echo "==> ai-service-secret (OPENAI_API_KEY provided)"
    kubectl create secret generic ai-service-secret \
      --from-literal="OPENAI_API_KEY=${OPENAI_API_KEY}" \
      --dry-run=client -o yaml | kubectl apply -f -
  else
    echo "    NOTE: OPENAI_API_KEY not set — skipping ai-service-secret."
    echo "          ai-service starts, but OpenAI embedding/chat calls fail until you create it:"
    echo "          OPENAI_API_KEY=sk-... devops/script/AiService/minikube-deploy.sh"
  fi
}

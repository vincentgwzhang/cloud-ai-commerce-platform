#!/usr/bin/env bash
# Install the Sealed Secrets (Bitnami) controller into the cluster + the kubeseal CLI locally.
#
# The controller holds a private key in-cluster and decrypts SealedSecret resources into
# normal Secrets. kubeseal (client) encrypts values against the controller's public cert,
# so the ciphertext is safe to commit to Git.
#
# Usage:
#   devops/argocd/sealed-secrets/install-sealed-secrets.sh
#
# Optional env:
#   SEALED_SECRETS_VERSION=v0.27.1   # default: latest GitHub release
#   CONTROLLER_NAMESPACE=kube-system
#   KUBESEAL_INSTALL_DIR=/usr/local/bin
set -euo pipefail

CONTROLLER_NAMESPACE="${CONTROLLER_NAMESPACE:-kube-system}"
KUBESEAL_INSTALL_DIR="${KUBESEAL_INSTALL_DIR:-/usr/local/bin}"
FALLBACK_VERSION="v0.27.1"

if ! kubectl cluster-info >/dev/null 2>&1; then
  echo "ERROR: kubectl cannot reach a cluster. Start one first: minikube start" >&2
  exit 1
fi

# --- Resolve release version (latest unless pinned) ---
VERSION="${SEALED_SECRETS_VERSION:-}"
if [[ -z "${VERSION}" ]]; then
  echo "==> Resolving latest sealed-secrets release"
  VERSION="$(curl -fsSL https://api.github.com/repos/bitnami-labs/sealed-secrets/releases/latest \
    | grep -oE '"tag_name": *"[^"]+"' | head -1 | cut -d'"' -f4 || true)"
  if [[ -z "${VERSION}" ]]; then
    echo "    Could not query GitHub API, falling back to ${FALLBACK_VERSION}"
    VERSION="${FALLBACK_VERSION}"
  fi
fi
VERSION_NO_V="${VERSION#v}"
echo "    Using sealed-secrets ${VERSION}"

# --- Install controller ---
CONTROLLER_URL="https://github.com/bitnami-labs/sealed-secrets/releases/download/${VERSION}/controller.yaml"
echo "==> Installing controller into namespace ${CONTROLLER_NAMESPACE}"
kubectl apply -f "${CONTROLLER_URL}"

echo "==> Waiting for sealed-secrets-controller rollout"
kubectl rollout status deployment/sealed-secrets-controller -n "${CONTROLLER_NAMESPACE}" --timeout=180s

# --- Install kubeseal CLI (best effort) ---
if command -v kubeseal >/dev/null 2>&1; then
  echo "==> kubeseal already installed: $(command -v kubeseal)"
else
  os="$(uname -s | tr '[:upper:]' '[:lower:]')"
  arch="$(uname -m)"
  case "${arch}" in
    x86_64|amd64) arch="amd64" ;;
    aarch64|arm64) arch="arm64" ;;
  esac
  tarball="kubeseal-${VERSION_NO_V}-${os}-${arch}.tar.gz"
  url="https://github.com/bitnami-labs/sealed-secrets/releases/download/${VERSION}/${tarball}"
  tmp="$(mktemp -d)"
  echo "==> Downloading kubeseal: ${url}"
  if curl -fsSL "${url}" -o "${tmp}/${tarball}"; then
    tar -xzf "${tmp}/${tarball}" -C "${tmp}" kubeseal
    if install -m 0755 "${tmp}/kubeseal" "${KUBESEAL_INSTALL_DIR}/kubeseal" 2>/dev/null; then
      echo "    Installed kubeseal -> ${KUBESEAL_INSTALL_DIR}/kubeseal"
    else
      echo "    Need elevated permission to write ${KUBESEAL_INSTALL_DIR}:"
      sudo install -m 0755 "${tmp}/kubeseal" "${KUBESEAL_INSTALL_DIR}/kubeseal"
      echo "    Installed kubeseal -> ${KUBESEAL_INSTALL_DIR}/kubeseal"
    fi
  else
    echo "    WARN: could not download kubeseal automatically." >&2
    echo "    Install manually: https://github.com/bitnami-labs/sealed-secrets/releases/tag/${VERSION}" >&2
  fi
  rm -rf "${tmp}"
fi

echo ""
echo "==> Sealed Secrets ready"
echo "    Next: OPENAI_API_KEY=sk-... devops/argocd/sealed-secrets/seal-ai-openai-key.sh"

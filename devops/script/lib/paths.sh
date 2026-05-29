#!/usr/bin/env bash
# Shared path resolution for devops scripts. Source after setting caller script path.
#
# Usage:
#   # shellcheck source=../lib/paths.sh
#   source "${DEVOPS_SCRIPT_DIR}/../lib/paths.sh"
#   devops_init_paths "${BASH_SOURCE[0]}"

devops_init_paths() {
  local caller="${1:?caller script required}"
  local script_dir
  script_dir="$(cd "$(dirname "${caller}")" && pwd)"

  case "${script_dir}" in
    */devops/script/AuthService)
      SERVICE_NAME="AuthService"
      DEVOPS_SCRIPT_SERVICE_DIR="${script_dir}"
      DEVOPS_SCRIPT_DIR="$(cd "${script_dir}/.." && pwd)"
      DEVOPS_ROOT="$(cd "${script_dir}/../.." && pwd)"
      ;;
    */devops/script/ProductService)
      SERVICE_NAME="ProductService"
      DEVOPS_SCRIPT_SERVICE_DIR="${script_dir}"
      DEVOPS_SCRIPT_DIR="$(cd "${script_dir}/.." && pwd)"
      DEVOPS_ROOT="$(cd "${script_dir}/../.." && pwd)"
      ;;
    */devops/script/InventoryService)
      SERVICE_NAME="InventoryService"
      DEVOPS_SCRIPT_SERVICE_DIR="${script_dir}"
      DEVOPS_SCRIPT_DIR="$(cd "${script_dir}/.." && pwd)"
      DEVOPS_ROOT="$(cd "${script_dir}/../.." && pwd)"
      ;;
    */devops/script/OrderService)
      SERVICE_NAME="OrderService"
      DEVOPS_SCRIPT_SERVICE_DIR="${script_dir}"
      DEVOPS_SCRIPT_DIR="$(cd "${script_dir}/.." && pwd)"
      DEVOPS_ROOT="$(cd "${script_dir}/../.." && pwd)"
      ;;
    */devops/script/GatewayService)
      SERVICE_NAME="GatewayService"
      DEVOPS_SCRIPT_SERVICE_DIR="${script_dir}"
      DEVOPS_SCRIPT_DIR="$(cd "${script_dir}/.." && pwd)"
      DEVOPS_ROOT="$(cd "${script_dir}/../.." && pwd)"
      ;;
    */devops/script/AiService)
      SERVICE_NAME="AiService"
      DEVOPS_SCRIPT_SERVICE_DIR="${script_dir}"
      DEVOPS_SCRIPT_DIR="$(cd "${script_dir}/.." && pwd)"
      DEVOPS_ROOT="$(cd "${script_dir}/../.." && pwd)"
      ;;
    */devops/script)
      SERVICE_NAME=""
      DEVOPS_SCRIPT_SERVICE_DIR=""
      DEVOPS_SCRIPT_DIR="${script_dir}"
      DEVOPS_ROOT="$(cd "${script_dir}/.." && pwd)"
      ;;
    *)
      echo "ERROR: devops_init_paths: unexpected script location: ${script_dir}" >&2
      return 1
      ;;
  esac

  REPO_ROOT="$(cd "${DEVOPS_ROOT}/.." && pwd)"
  JWT_KEYS_DIR="${DEVOPS_ROOT}/data/keys"

  if [[ -n "${SERVICE_NAME}" ]]; then
    SERVICE_ROOT="${REPO_ROOT}/${SERVICE_NAME}"
    K8S_DIR="${DEVOPS_ROOT}/k8s/${SERVICE_NAME}"
    if [[ ! -f "${SERVICE_ROOT}/pom.xml" ]]; then
      echo "ERROR: Maven project not found at ${SERVICE_ROOT}/pom.xml" >&2
      return 1
    fi
  fi
}

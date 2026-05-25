#!/usr/bin/env bash
# Expose Minikube actuator endpoints for external Prometheus (NodePort, no port-forward).
#
# Usage:
#   ./devops/script/observability/minikube-metrics-apply.sh
#
# Then start the observability stack (standalone compose project):
#   docker compose -f devops/script/docker-compose-observability-minikube.yml up -d
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEVOPS_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
MANIFEST="${DEVOPS_ROOT}/k8s/observability/metrics-nodeport-services.yaml"

if ! minikube status >/dev/null 2>&1; then
  echo "ERROR: minikube is not running. Start with: minikube start" >&2
  exit 1
fi

if ! docker network inspect minikube >/dev/null 2>&1; then
  echo "ERROR: Docker network 'minikube' not found (expected with minikube docker driver)." >&2
  exit 1
fi

echo "==> Applying metrics NodePort services"
kubectl apply -f "${MANIFEST}"

echo ""
echo "==> Verifying scrape paths (via minikube Docker network)"
sleep 2
for spec in "30080:auth-service" "30081:product-service" "30082:inventory-service" "30083:order-service" "30088:gateway-service"; do
  port="${spec%%:*}"
  name="${spec##*:}"
  code="$(docker run --rm --network minikube curlimages/curl:8.5.0 -s -o /dev/null -w '%{http_code}' --connect-timeout 5 "http://minikube:${port}/actuator/health" 2>/dev/null || true)"
  code="${code:-000}"
  if [[ "${code}" == "200" ]]; then
    echo "  OK  ${name}  minikube:${port}/actuator/prometheus"
  else
    echo "  FAIL ${name}  minikube:${port}  (HTTP ${code}) — is the deployment running?" >&2
  fi
done

echo ""
echo "==> Next: start Grafana + Prometheus for Minikube"
echo "    docker compose -f devops/script/docker-compose-observability-minikube.yml up -d"
echo ""
echo "    Grafana:     http://localhost:3000  (admin / admin)"
echo "    Prometheus:  http://localhost:9090/targets"
echo "    Docs:        devops/docs/observability/minikube-grafana.md"

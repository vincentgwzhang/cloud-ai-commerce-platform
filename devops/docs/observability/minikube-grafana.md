# Grafana + Prometheus with Minikube workloads

When microservices run **inside Minikube** (ClusterIP, no `kubectl port-forward`), Prometheus in Docker cannot reach pod or ClusterIP addresses. This setup uses a standard dev pattern:

1. **Dedicated NodePort Services** for metrics only (business traffic stays ClusterIP).
2. **Prometheus joins the `minikube` Docker network** and scrapes `minikube:<nodePort>`.

Works with the **Minikube Docker driver** (default on Linux). No port-forward, no per-service tunnel.

## Architecture

```text
┌─ Minikube cluster ─────────────────────────────┐
│  auth-service (ClusterIP)  ← normal in-cluster │
│  auth-service-metrics (NodePort 30080)         │
│       ↑ kube-proxy on minikube node            │
└───────┼────────────────────────────────────────┘
        │ Docker network "minikube"
┌───────┴────────────────────────────────────────┐
│  prometheus (docker-compose) → minikube:30080  │
│  grafana (docker-compose)    → prometheus:9090 │
└────────────────────────────────────────────────┘
```

## Quick start

```bash
# 1. Services already deployed (install.sh)
./devops/script/install.sh

# 2. Create metrics NodePort services + verify
chmod +x devops/script/observability/minikube-metrics-apply.sh
./devops/script/observability/minikube-metrics-apply.sh

# 3. Start Kafka and observability (separate compose projects)
docker compose -f devops/script/docker-compose-app.yml up -d
docker compose -f devops/script/docker-compose-observability-minikube.yml up -d

# 4. Open Grafana
# http://localhost:3000 — admin / admin
# Check targets: http://localhost:9090/targets — all jobs UP
```

## NodePort mapping

| Metrics Service | NodePort | App port | Scrape path |
|-----------------|----------|----------|-------------|
| auth-service-metrics | 30080 | 8080 | /actuator/prometheus |
| product-service-metrics | 30081 | 8081 | /actuator/prometheus |
| inventory-service-metrics | 30082 | 8082 | /actuator/prometheus |
| order-service-metrics | 30083 | 8083 | /actuator/prometheus |
| gateway-service-metrics | 30088 | 8088 | /actuator/prometheus |

Manifest: [metrics-nodeport-services.yaml](../../k8s/observability/metrics-nodeport-services.yaml)

## Why not port-forward?

| Approach | Pros | Cons |
|----------|------|------|
| `kubectl port-forward` | Simple | One process per service; dies on disconnect |
| **NodePort + minikube network** | Stable; no extra processes; compose-friendly | Dev-only; fixed ports |
| Prometheus in-cluster | Production-grade | Grafana still external unless also in-cluster |
| `kubernetes_sd_configs` + kubeconfig | Auto-discovery | Pod IPs often unreachable from default Docker bridge |

## Switch back to local IntelliJ services

Stop the Minikube observability stack, then start the local one (scrapes `host.docker.internal:8080–8088`):

```bash
docker compose -f devops/script/docker-compose-observability-minikube.yml down
docker compose -f devops/script/docker-compose-observability-local.yml up -d
```

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| Prometheus target DOWN | Run `minikube-metrics-apply.sh`; confirm pods `kubectl get pods` |
| `network minikube not found` | Start Minikube: `minikube start` (creates Docker network) |
| 401 on scrape | `/actuator/prometheus` must be permitAll in SecurityConfig |
| NodePort conflict | Change `nodePort` in metrics manifest (range 30000–32767) |
| VM driver (not Docker) | Use `minikube ip` in `prometheus.minikube.yml` instead of hostname `minikube`, or attach Prometheus with `network_mode: host` on Linux |

## Files

```text
devops/k8s/observability/metrics-nodeport-services.yaml
devops/observability/prometheus.minikube.yml
devops/script/docker-compose-observability-minikube.yml
devops/script/docker-compose-observability-local.yml
devops/script/observability/minikube-metrics-apply.sh
```

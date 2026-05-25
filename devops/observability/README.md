# Grafana + Prometheus (local)

Docker Compose stacks for **Prometheus** (metrics scrape) and **Grafana** (dashboards). Kafka is in `docker-compose-app.yml`.

Spring Boot services run on the **host** (IntelliJ or `docker-run.sh`); Prometheus inside Docker scrapes them via `host.docker.internal`.

## Quick start

```bash
# 1. Start Kafka
docker compose -f devops/script/docker-compose-app.yml up -d

# 2. Start observability (pick one)
docker compose -f devops/script/docker-compose-observability-local.yml up -d

# 2. Run microservices on host (ports 8080–8083, 8088)
#    Auth 8080, Product 8081, Inventory 8082, Order 8083, Gateway 8088

# 3. Open Grafana
open http://localhost:3000
# Login: admin / admin
```

The home dashboard **Commerce Platform Overview** loads automatically (`commerce-overview`).

## URLs

| Service | URL |
|---------|-----|
| Grafana | http://localhost:3000 |
| Prometheus | http://localhost:9090 |
| Prometheus targets | http://localhost:9090/targets |
| Kafka UI | http://localhost:18080 |

## What the dashboard shows

- **Service availability** — scrape `up` per job (red DOWN / green UP)
- **HTTP** — request rate and p95 latency per service
- **Auth** — `login_success_total` / `login_failure_total`, JVM heap
- **Product** — cache hit/miss rates
- **Inventory** — reservations, locks, Kafka consume/publish
- **Order** — created/failed, event publish, Kafka failures
- **Gateway** — request rate by route (`uri` label)

Metrics naming: [metrics-naming.md](../docs/observability/metrics-naming.md).

## Minikube (no port-forward)

Services in Minikube use ClusterIP — Docker Prometheus cannot scrape them via `host.docker.internal`.

**Recommended:** NodePort metrics services + Prometheus on the `minikube` Docker network.

```bash
./devops/script/observability/minikube-metrics-apply.sh
docker compose -f devops/script/docker-compose-observability-minikube.yml up -d
```

See [Minikube + Grafana](../docs/observability/minikube-grafana.md).

## Scrape targets (local IntelliJ)

Configured in [prometheus.yml](prometheus.yml):

| Job | Host port |
|-----|-----------|
| auth-service | 8080 |
| product-service | 8081 |
| inventory-service | 8082 |
| order-service | 8083 |
| gateway-service | 8088 |

Path: `/actuator/prometheus` on each service.

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| Target DOWN in Prometheus | Ensure the Spring Boot app is running on that port |
| Empty business metrics | Generate traffic (login, create order, etc.) — counters start at 0 |
| 401 on scrape | Actuator `/actuator/prometheus` must be permitAll in SecurityConfig |
| `host.docker.internal` fails | Linux needs `extra_hosts: host-gateway` (already in compose) |

Reload Prometheus config after editing `prometheus.yml`:

```bash
curl -X POST http://localhost:9090/-/reload
```

## Files

```text
devops/observability/
├── prometheus.yml
├── grafana/
│   ├── dashboards/commerce-platform-overview.json
│   └── provisioning/
│       ├── datasources/prometheus.yml
│       └── dashboards/dashboards.yml
```

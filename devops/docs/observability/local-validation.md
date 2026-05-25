# Local observability validation

## Prerequisites

Services running locally (or port-forward from Minikube). Example ports: Auth 8080, Product 8081, Inventory 8082, Order 8083, Gateway 8088.

## 1. Prometheus metrics

```bash
curl -s http://localhost:8083/actuator/prometheus | grep order_created_total
curl -s http://localhost:8082/actuator/prometheus | grep inventory_reservation
curl -s http://localhost:8081/actuator/prometheus | grep product_cache
curl -s http://localhost:8080/actuator/prometheus | grep login_success
```

## 2. Request correlation

```bash
curl -s -H 'X-Request-Id: demo-correlation-001' \
  http://localhost:8083/api/orders/health -v 2>&1 | grep -i x-request-id
```

Check service logs for `requestId=demo-correlation-001`.

## 3. Health dependencies

```bash
curl -s http://localhost:8082/actuator/health | jq
curl -s http://localhost:8083/actuator/health | jq
```

Expect `db`, `redis`, and (for Order/Inventory) `kafka` components when infrastructure is up.

## 4. Kafka business flow

1. Start Kafka: `docker compose -f devops/script/docker-compose-app.yml up -d kafka`
2. Create order with `X-Request-Id` header via Gateway or Order API
3. Grep logs: `ORDER_CREATED`, `INVENTORY_RESERVED`, `business_event`
4. Confirm metrics increment on both services

## 5. Slow request logging

Repeat a protected endpoint; requests slower than ~1s log `Slow HTTP request` at WARN.

## 6. Grafana dashboard

```bash
docker compose -f devops/script/docker-compose-observability-local.yml up -d
```

Open http://localhost:3000 (admin / admin). Home dashboard **Commerce Platform Overview** shows all five services.

Verify Prometheus targets: http://localhost:9090/targets — all jobs should be **UP** while apps run on host.

## Troubleshooting

| Symptom | Check |
|---------|--------|
| 401 on `/actuator/prometheus` | Security permit list includes prometheus |
| No `requestId` in logs | `RequestCorrelationFilter` registered |
| Kafka health DOWN | `localhost:9092` and `KAFKA_BOOTSTRAP_SERVERS` |
| Missing traceId | `micrometer-tracing-bridge-otel` on classpath |

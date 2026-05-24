# OrderService

Order orchestration with Kafka (Saga choreography demo). Port **8083**.

## Prerequisites

```bash
mysql -u vincent -p commerce_platform < devops/db/init.sql
docker compose -f devops/script/docker-compose-app.yml up -d kafka kafka-ui
./devops/script/local-dev-setup.sh
```

Run **InventoryService (8082)** and **OrderService (8083)** for async saga. See [devops/docs/kafka-event-driven-phase1.md](../devops/docs/kafka-event-driven-phase1.md).

## APIs

| Method | Path | Auth |
|--------|------|------|
| GET | `/api/orders/health` | No |
| POST | `/api/orders` | JWT |
| GET | `/api/orders/{orderNo}` | JWT |
| GET | `/api/orders/status/{orderNo}` | JWT |
| POST | `/api/orders/{orderNo}/cancel` | JWT |
| POST | `/api/orders/demo/concurrent-create` | No |

## Create order (async inventory)

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"vincent","password":"123456"}' | jq -r .accessToken)

curl -s -X POST http://localhost:8083/api/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"productCode":"IPHONE17","quantity":1,"requestId":"'$(uuidgen)'"}' | jq
```

Poll `GET /api/orders/status/{orderNo}` until status is `CONFIRMED` or `FAILED`.

## Kafka topics

| Topic | Role |
|-------|------|
| `order-created` | Published on new order |
| `inventory-reserved` | Consumed from inventory-service |
| `inventory-failed` | Consumed |
| `order-confirmed` | Published when saga completes |
| `order-failed` | Published on failure |
| `order-dlq` | Dead letter after consumer retries |

Kafka UI: http://localhost:18080

## Minikube

```bash
./devops/script/OrderService/minikube-deploy.sh
kubectl port-forward svc/order-service 8083:80
```

K8s: `devops/k8s/OrderService/` — Kafka via `host.minikube.internal:9092`.

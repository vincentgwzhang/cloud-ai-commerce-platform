# InventoryService

Distributed inventory demo: reservation, anti-overselling, Redis atomic ops, idempotency, optimistic locking.

Port **8082**. JWT aligned with AuthService (`devops/data/keys/public.pem`).

## Kafka (Phase 1)

Consumes `order-created`, reserves stock via existing `reserve()`, publishes `inventory-reserved` or `inventory-failed`.

See [devops/docs/kafka-event-driven-phase1.md](../devops/docs/kafka-event-driven-phase1.md).

**Local:** start this service before or with OrderService so saga completes.

## APIs

| Method | Path | Auth |
|--------|------|------|
| GET | `/api/inventory/health` | No |
| GET | `/api/inventory/{productCode}` | JWT |
| POST | `/api/inventory/reserve` | JWT |
| POST | `/api/inventory/release` | JWT |
| POST | `/api/inventory/deduct` | JWT |
| POST | `/api/inventory/demo/concurrent-reserve` | No (demo) |

## Local

```bash
# From repo root
./devops/script/local-dev-setup.sh
docker compose -f devops/script/docker-compose-app.yml up -d kafka
cd InventoryService && mvn spring-boot:run
```

## Sample curls (HTTP reserve)

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"vincent","password":"123456"}' | jq -r .accessToken)

curl -s http://localhost:8082/api/inventory/IPHONE17 \
  -H "Authorization: Bearer $TOKEN" | jq

curl -s -X POST http://localhost:8082/api/inventory/reserve \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"productCode":"IPHONE17","quantity":1,"requestId":"'$(uuidgen)'"}' | jq
```

## Concurrency demo

```bash
curl -s -X POST http://localhost:8082/api/inventory/demo/concurrent-reserve \
  -H 'Content-Type: application/json' \
  -d '{"productCode":"RTX5090","concurrentRequests":100,"quantityPerRequest":1}' | jq
```

## Minikube

```bash
./devops/script/install.sh          # all services
# or only inventory (after Auth):
./devops/script/InventoryService/minikube-deploy.sh
```

K8s manifests: `devops/k8s/InventoryService/`

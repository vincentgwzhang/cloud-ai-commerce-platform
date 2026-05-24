# Kafka event-driven architecture — Phase 1

Gradual evolution: **order-service** and **inventory-service** communicate via Kafka (Saga choreography style). No distributed transaction framework.

## Flow

```
POST /api/orders
    → order CREATED (MySQL)
    → publish order-created
    → inventory-service consumes
    → reserve() (existing logic + Redis idempotency)
    → publish inventory-reserved OR inventory-failed
    → order-service consumes
    → order INVENTORY_RESERVED → CONFIRMED (or FAILED)
```

## Prerequisites

```bash
mysql -u vincent -p commerce_platform < devops/db/init.sql
docker compose -f devops/script/docker-compose-app.yml up -d kafka kafka-ui
./devops/script/local-dev-setup.sh
```

Kafka UI: http://localhost:18080

## Local run (IntelliJ or CLI)

Start in order:

1. AuthService (8080)
2. ProductService (8081) — optional for catalog
3. **InventoryService (8082)** — must be running to consume `order-created`
4. **OrderService (8083)**

## End-to-end test

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"vincent","password":"123456"}' | jq -r .accessToken)

REQUEST_ID=$(uuidgen)
curl -s -X POST http://localhost:8083/api/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"productCode\":\"IPHONE17\",\"quantity\":1,\"requestId\":\"$REQUEST_ID\"}" | jq

# Poll status until CONFIRMED or FAILED (async, usually < 2s)
ORDER_NO=<orderNo from response>
curl -s "http://localhost:8083/api/orders/status/$ORDER_NO" \
  -H "Authorization: Bearer $TOKEN" | jq
```

## Verify Kafka

```bash
# List topics
docker exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list

# Tail order-created
docker exec -it kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic order-created \
  --from-beginning
```

## Topics

| Topic | Producer | Consumer |
|-------|----------|----------|
| `order-created` | order-service | inventory-service |
| `inventory-reserved` | inventory-service | order-service |
| `inventory-failed` | inventory-service | order-service |
| `order-dlq` | (retry exhaust) | order-service consumer |
| `inventory-dlq` | (retry exhaust) | inventory-service consumer |

## Idempotency

- **Orders:** `requestId` on create — Redis SETNX + DB unique constraint.
- **Inventory:** same `requestId` from `ORDER_CREATED` event — duplicate Kafka delivery does not double-reserve.

## Metrics (Prometheus)

| Metric | Service |
|--------|---------|
| `order_event_published_total` | order |
| `kafka_consume_failure_total` | order |
| `inventory_order_created_consumed_total` | inventory |
| `inventory_event_published_total` | inventory |
| `inventory_reservation_success_total` | inventory |

## Minikube

Kafka on host: `host.minikube.internal:9092`. Deploy inventory + order after Kafka compose is up:

```bash
./devops/script/install.sh
```

## Failure scenarios (learning)

1. **Insufficient stock** — order ends `FAILED`; topic `inventory-failed`.
2. **Poison message** — after retries, message goes to `order-dlq` or `inventory-dlq`.
3. **Duplicate event** — safe replays via idempotency keys.

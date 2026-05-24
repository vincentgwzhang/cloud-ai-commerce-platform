# Metrics naming guide

Convention: `{domain}_{action}_total` for counters, `{domain}_{name}` for timers/gauges.

## Auth (`auth-service`)

| Metric | Type |
|--------|------|
| `login_success_total` | counter |
| `login_failure_total` | counter |

## Product (`product-service`)

| Metric | Type |
|--------|------|
| `product_cache_hit_total` | counter |
| `product_cache_miss_total` | counter |

## Inventory (`inventory-service`)

| Metric | Type |
|--------|------|
| `inventory_reservation_success_total` | counter |
| `inventory_reservation_failure_total` | counter |
| `inventory_cache_hit_total` | counter |
| `inventory_cache_miss_total` | counter |
| `inventory_lock_acquired_total` | counter |
| `inventory_lock_failed_total` | counter |
| `inventory_idempotency_duplicate_total` | counter |
| `inventory_order_created_consumed_total` | counter |
| `inventory_event_published_total` | counter |
| `inventory_kafka_consume_failure_total` | counter |

## Order (`order-service`)

| Metric | Type |
|--------|------|
| `order_created_total` | counter |
| `order_failed_total` | counter |
| `order_event_published_total` | counter |
| `kafka_consume_failure_total` | counter |

## HTTP

Spring Boot exposes `http.server.requests` automatically (latency histogram per route).

# Redis optimization phase

Incremental cache engineering across Product, Inventory, and Order services.

## Key naming

| Service | Keys |
|---------|------|
| Product | `product:detail:{id}`, `product:notfound:{id}`, `product:hot:list` |
| Inventory | `inventory:product:{code}`, `inventory:request:{requestId}`, `inventory:lock:{code}` |
| Order | `order:detail:{orderNo}`, `order:request:{requestId}` |

See `*RedisKeys.java` in each service.

## TTL strategy

- Product detail: 10m (+ jitter)
- Hot products / list: 30m
- Null-not-found guard: 2m
- Inventory stock / query: 30m
- Idempotency: 24h

Jitter spreads expirations to reduce **cache avalanche**.

## Patterns implemented

| Pattern | Where |
|---------|--------|
| Cache-aside + SETNX lock | Product detail, Inventory GET |
| Null cache (penetration) | Product `product:notfound:{id}` |
| Local L1 hot cache | Product hot IDs, Inventory hot SKUs |
| Cache warming | `HotProductPreloader`, `InventoryCacheWarmer` |
| Delayed double-delete | Inventory query view after writes |
| Idempotency SETNX | Order create, Inventory reserve |
| Redis-safe degradation | All services — DB fallback if Redis down |

## Metrics (Prometheus)

- `cache_hit_total` / `cache_miss_total` — Product
- `inventory_cache_hit_total` / `inventory_cache_miss_total` — Inventory
- `idempotency_duplicate_total` — Order (and `inventory_idempotency_duplicate_total`)

## Manual tests

### Cache hit (Product)

```bash
curl -s http://localhost:8081/api/products/1 -H "Authorization: Bearer $TOKEN"
# Repeat — check logs for "Redis cache hit" or metric cache_hit_total
```

### Cache penetration (Product)

```bash
curl -s http://localhost:8081/api/products/999999 -H "Authorization: Bearer $TOKEN"
# Repeat twice — second request should not hammer DB (null cache)
```

### Idempotency (Order)

```bash
REQ=$(uuidgen)
curl -s -X POST http://localhost:8083/api/orders -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"productCode\":\"IPHONE17\",\"quantity\":1,\"requestId\":\"$REQ\"}"
# Repeat same body — same orderNo, metric idempotency_duplicate_total increases
```

### Redis down (graceful degradation)

```bash
sudo systemctl stop redis-server   # or: docker stop <redis-container>
# Product/Inventory/Order should still respond via MySQL (slower)
sudo systemctl start redis-server
```

### Hot inventory

```bash
# Start InventoryService — logs "Warmed N hot inventory SKUs"
curl -s http://localhost:8082/api/inventory/IPHONE17 -H "Authorization: Bearer $TOKEN"
```

## Concurrency (inventory)

```bash
curl -s -X POST http://localhost:8082/api/inventory/demo/concurrent-reserve \
  -H 'Content-Type: application/json' \
  -d '{"productCode":"RTX5090","concurrentRequests":50,"quantityPerRequest":1}'
```

## Kafka + Redis together

See [kafka-event-driven-phase1.md](./kafka-event-driven-phase1.md) — inventory idempotency uses the same `requestId` from `ORDER_CREATED` events.

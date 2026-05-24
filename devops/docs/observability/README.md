# Observability overview

Lightweight cloud-native visibility for the commerce platform — no ELK/Jaeger deployment in this phase.

## Pillars

| Pillar | Implementation |
|--------|----------------|
| Metrics | Micrometer + `/actuator/prometheus` |
| Logs | MDC `requestId` / `traceId` in console pattern |
| Traces | Micrometer OTel bridge (propagation prep; no Jaeger yet) |
| Health | Actuator + DB / Redis / Kafka indicators |

## Request flow

```
Client → Gateway (8088) → Product / Inventory / Order
         X-Request-Id      same header forwarded
         traceparent       W3C via Micrometer tracing
```

Login stays on **Auth (8080)**. Use the same `X-Request-Id` on downstream calls when testing correlation manually.

## Key endpoints

| Endpoint | Purpose |
|----------|---------|
| `/actuator/health` | Liveness / dependency status |
| `/actuator/info` | Service metadata |
| `/actuator/metrics` | Metric names |
| `/actuator/prometheus` | Prometheus scrape |

## Docs

- [Metrics naming](metrics-naming.md)
- [Local validation](local-validation.md)

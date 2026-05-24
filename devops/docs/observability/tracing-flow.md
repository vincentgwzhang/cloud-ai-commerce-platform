# Tracing and correlation flow

## HTTP

1. Client sends optional `X-Request-Id` (Postman can set a fixed value for demos).
2. `RequestCorrelationFilter` (servlet services) or `GatewayCorrelationWebFilter` assigns/propagates the ID.
3. Micrometer tracing adds `traceId` to MDC when `micrometer-tracing-bridge-otel` is on the classpath.
4. Response echoes `X-Request-Id` and `X-Trace-Id` headers.

## Kafka

`OrderCreatedEvent` carries `requestId` and `traceId` from the HTTP request that created the order.

Inventory consumer restores them into MDC before processing — logs remain searchable across async steps.

## Async tracing challenge

Kafka processing happens on another thread and service. Without propagating IDs in the event payload, you only see broker offsets in logs — not the original user request. This phase prepares for a future Jaeger/Tempo backend without deploying it yet.

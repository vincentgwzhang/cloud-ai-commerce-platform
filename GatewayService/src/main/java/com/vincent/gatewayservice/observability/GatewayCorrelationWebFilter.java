package com.vincent.gatewayservice.observability;

import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class GatewayCorrelationWebFilter implements WebFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(GatewayCorrelationWebFilter.class);
    private static final long SLOW_REQUEST_MS = 1_000L;

    private final ObjectProvider<Tracer> tracerProvider;

    public GatewayCorrelationWebFilter(ObjectProvider<Tracer> tracerProvider) {
        this.tracerProvider = tracerProvider;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String requestId = request.getHeaders().getFirst(CorrelationHeaders.REQUEST_ID);
        if (!StringUtils.hasText(requestId)) {
            requestId = UUID.randomUUID().toString();
        }
        final String correlationId = requestId;

        MdcSupport.enrichFromTracer(tracerProvider.getIfAvailable());
        String traceId = MdcSupport.traceId().orElse(null);

        ServerHttpRequest mutated = request.mutate()
                .header(CorrelationHeaders.REQUEST_ID, correlationId)
                .headers(headers -> {
                    if (traceId != null) {
                        headers.set(CorrelationHeaders.TRACE_ID, traceId);
                    }
                })
                .build();

        long startNanos = System.nanoTime();
        return chain.filter(exchange.mutate().request(mutated).build())
                .doOnEach(signal -> {
                    if (signal.isOnComplete() || signal.isOnError()) {
                        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
                        String path = request.getURI().getPath();
                        if (elapsedMs >= SLOW_REQUEST_MS && !path.startsWith("/actuator")) {
                            log.warn("Slow gateway request method={} uri={} durationMs={} requestId={}",
                                    request.getMethod(), path, elapsedMs, correlationId);
                        }
                    }
                })
                .contextWrite(ctx -> ctx.put(MdcKeys.REQUEST_ID, correlationId))
                .doFinally(signalType -> {
                    MDC.remove(MdcKeys.REQUEST_ID);
                    MDC.remove(MdcKeys.TRACE_ID);
                });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}

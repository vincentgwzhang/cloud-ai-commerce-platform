package com.vincent.inventoryservice.observability;

import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Assigns/propagates {@link CorrelationHeaders#REQUEST_ID} and exposes traceId in MDC.
 *
 * <p>Correlation IDs tie together logs across services without a full trace backend —
 * the first step toward distributed tracing.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestCorrelationFilter.class);
    private static final long SLOW_REQUEST_MS = 1_000L;

    private final ObjectProvider<Tracer> tracerProvider;

    public RequestCorrelationFilter(ObjectProvider<Tracer> tracerProvider) {
        this.tracerProvider = tracerProvider;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = request.getHeader(CorrelationHeaders.REQUEST_ID);
        if (!StringUtils.hasText(requestId)) {
            requestId = UUID.randomUUID().toString();
        }

        MDC.put(MdcKeys.REQUEST_ID, requestId);
        response.setHeader(CorrelationHeaders.REQUEST_ID, requestId);

        Tracer tracer = tracerProvider.getIfAvailable();
        MdcSupport.enrichFromTracer(tracer);
        MdcSupport.traceId().ifPresent(traceId -> response.setHeader(CorrelationHeaders.TRACE_ID, traceId));

        long startNanos = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
            if (elapsedMs >= SLOW_REQUEST_MS && !request.getRequestURI().startsWith("/actuator")) {
                log.warn("Slow HTTP request method={} uri={} status={} durationMs={} requestId={}",
                        request.getMethod(), request.getRequestURI(), response.getStatus(), elapsedMs, requestId);
            }
            MDC.remove(MdcKeys.REQUEST_ID);
            MDC.remove(MdcKeys.TRACE_ID);
            MdcSupport.clearBusinessContext();
        }
    }
}

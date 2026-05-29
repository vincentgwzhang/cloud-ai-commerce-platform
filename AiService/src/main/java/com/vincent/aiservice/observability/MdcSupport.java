package com.vincent.aiservice.observability;

import io.micrometer.tracing.Tracer;
import org.slf4j.MDC;

import java.util.Optional;

/** Bridges Micrometer trace context and SLF4J MDC for structured logs. */
public final class MdcSupport {

    private MdcSupport() {
    }

    public static void put(String key, String value) {
        if (value != null && !value.isBlank()) {
            MDC.put(key, value);
        }
    }

    public static void remove(String key) {
        MDC.remove(key);
    }

    public static Optional<String> get(String key) {
        return Optional.ofNullable(MDC.get(key));
    }

    public static void enrichFromTracer(Tracer tracer) {
        if (tracer == null) {
            return;
        }
        var span = tracer.currentSpan();
        if (span != null) {
            put(MdcKeys.TRACE_ID, span.context().traceId());
        }
    }

    public static Optional<String> traceId() {
        return get(MdcKeys.TRACE_ID);
    }

    public static Optional<String> requestId() {
        return get(MdcKeys.REQUEST_ID);
    }

    public static void putBusinessContext(String username, String productCode, String eventId) {
        put(MdcKeys.USERNAME, username);
        put(MdcKeys.PRODUCT_CODE, productCode);
        put(MdcKeys.EVENT_ID, eventId);
    }

    public static void clearBusinessContext() {
        remove(MdcKeys.USERNAME);
        remove(MdcKeys.PRODUCT_CODE);
        remove(MdcKeys.EVENT_ID);
    }
}

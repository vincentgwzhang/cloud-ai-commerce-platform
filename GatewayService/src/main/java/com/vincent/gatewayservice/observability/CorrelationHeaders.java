package com.vincent.gatewayservice.observability;

/** HTTP headers for cross-service correlation (distributed tracing prep). */
public final class CorrelationHeaders {

    public static final String REQUEST_ID = "X-Request-Id";
    public static final String TRACE_ID = "X-Trace-Id";

    private CorrelationHeaders() {
    }
}

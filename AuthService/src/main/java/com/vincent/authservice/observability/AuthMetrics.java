package com.vincent.authservice.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class AuthMetrics {

    private final Counter loginSuccess;
    private final Counter loginFailure;

    public AuthMetrics(MeterRegistry meterRegistry) {
        this.loginSuccess = meterRegistry.counter("login_success_total");
        this.loginFailure = meterRegistry.counter("login_failure_total");
    }

    public void recordLoginSuccess() {
        loginSuccess.increment();
    }

    public void recordLoginFailure() {
        loginFailure.increment();
    }
}

package com.vincent.gatewayservice.observability;

import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "management.tracing.sampling.probability=1.0"
})
class TracingConfigurationTest {

    @Autowired
    private ObjectProvider<Tracer> tracerProvider;

    @Test
    void tracerBeanIsAvailableForMdcEnrichment() {
        assertThat(tracerProvider.getIfAvailable()).isNotNull();
    }
}

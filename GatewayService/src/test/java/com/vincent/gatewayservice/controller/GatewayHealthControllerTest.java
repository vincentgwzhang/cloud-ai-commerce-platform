package com.vincent.gatewayservice.controller;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayHealthControllerTest {

    private final GatewayHealthController controller = new GatewayHealthController();

    @Test
    void healthIsPublic() {
        Map<String, String> response = controller.health().block();

        assertThat(response)
                .containsEntry("status", "UP")
                .containsEntry("service", "gateway-service");
    }
}

package com.vincent.gatewayservice.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
public class GatewayHealthController {

    @GetMapping("/api/gateway/health")
    public Mono<Map<String, String>> health() {
        return Mono.just(Map.of("status", "UP", "service", "gateway-service"));
    }
}

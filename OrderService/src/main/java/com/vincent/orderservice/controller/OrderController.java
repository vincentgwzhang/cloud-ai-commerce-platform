package com.vincent.orderservice.controller;

import com.vincent.orderservice.dto.ApiResponse;
import com.vincent.orderservice.dto.ConcurrentOrderDemoRequest;
import com.vincent.orderservice.dto.CreateOrderRequest;
import com.vincent.orderservice.dto.HealthResponse;
import com.vincent.orderservice.dto.OrderResponse;
import com.vincent.orderservice.service.OrderConcurrencyDemoService;
import com.vincent.orderservice.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final OrderConcurrencyDemoService concurrencyDemoService;

    public OrderController(OrderService orderService, OrderConcurrencyDemoService concurrencyDemoService) {
        this.orderService = orderService;
        this.concurrencyDemoService = concurrencyDemoService;
    }

    @GetMapping("/health")
    public ApiResponse<HealthResponse> health() {
        return ApiResponse.ok(new HealthResponse("UP"));
    }

    @PostMapping
    public ApiResponse<OrderResponse> create(
            Principal principal,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        // For the JWT resource server, principal is a JwtAuthenticationToken whose name is the
        // token subject (the username). Null on unauthenticated demo flows.
        String username = principal != null ? principal.getName() : null;
        return ApiResponse.ok("created", orderService.createOrder(request, username));
    }

    @GetMapping("/{orderNo}")
    public ApiResponse<OrderResponse> get(@PathVariable String orderNo) {
        return ApiResponse.ok(orderService.getOrder(orderNo));
    }

    @GetMapping("/status/{orderNo}")
    public ApiResponse<OrderResponse> status(@PathVariable String orderNo) {
        return ApiResponse.ok(orderService.getOrderStatus(orderNo));
    }

    @PostMapping("/{orderNo}/cancel")
    public ApiResponse<OrderResponse> cancel(@PathVariable String orderNo) {
        return ApiResponse.ok("cancelled", orderService.cancelOrder(orderNo));
    }

    @PostMapping("/demo/concurrent-create")
    public ApiResponse<OrderConcurrencyDemoService.DemoResult> concurrentDemo(
            @Valid @RequestBody ConcurrentOrderDemoRequest request
    ) {
        return ApiResponse.ok(concurrencyDemoService.runConcurrentCreate(request));
    }
}

package com.vincent.orderservice.service;

import com.vincent.orderservice.dto.ConcurrentOrderDemoRequest;
import com.vincent.orderservice.dto.CreateOrderRequest;
import com.vincent.orderservice.dto.OrderResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stress-test idempotency: many threads, same requestId → one order row.
 */
@Service
public class OrderConcurrencyDemoService {

    private final OrderService orderService;

    public OrderConcurrencyDemoService(OrderService orderService) {
        this.orderService = orderService;
    }

    public DemoResult runConcurrentCreate(ConcurrentOrderDemoRequest request) {
        String sharedRequestId = UUID.randomUUID().toString();
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();
        List<String> orderNos = new ArrayList<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (int i = 0; i < request.concurrentRequests(); i++) {
                futures.add(CompletableFuture.runAsync(() -> {
                    try {
                        OrderResponse response = orderService.createOrder(
                                new CreateOrderRequest(
                                        request.productCode(),
                                        request.quantityPerRequest(),
                                        sharedRequestId
                                )
                        );
                        synchronized (orderNos) {
                            orderNos.add(response.orderNo());
                        }
                        success.incrementAndGet();
                    } catch (RuntimeException ex) {
                        failure.incrementAndGet();
                    }
                }, executor));
            }
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        }

        long distinctOrders = orderNos.stream().distinct().count();
        return new DemoResult(
                sharedRequestId,
                request.concurrentRequests(),
                success.get(),
                failure.get(),
                distinctOrders
        );
    }

    public record DemoResult(
            String requestId,
            int concurrentRequests,
            int successCount,
            int failureCount,
            long distinctOrderNos
    ) {
    }
}

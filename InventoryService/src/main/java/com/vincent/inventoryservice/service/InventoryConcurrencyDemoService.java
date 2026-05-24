package com.vincent.inventoryservice.service;

import com.vincent.inventoryservice.dto.ConcurrentDemoRequest;
import com.vincent.inventoryservice.dto.InventoryResponse;
import com.vincent.inventoryservice.repository.InventoryRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class InventoryConcurrencyDemoService {

    private final InventoryService inventoryService;
    private final InventoryRepository inventoryRepository;

    public InventoryConcurrencyDemoService(InventoryService inventoryService, InventoryRepository inventoryRepository) {
        this.inventoryService = inventoryService;
        this.inventoryRepository = inventoryRepository;
    }

    public DemoResult runConcurrentReserve(ConcurrentDemoRequest request) {
        int beforeAvailable = inventoryRepository.findByProductCode(request.productCode())
                .map(inv -> inv.getAvailableStock())
                .orElse(0);

        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (int i = 0; i < request.concurrentRequests(); i++) {
                futures.add(CompletableFuture.runAsync(() -> {
                    try {
                        inventoryService.reserve(
                                request.productCode(),
                                request.quantityPerRequest(),
                                UUID.randomUUID().toString()
                        );
                        success.incrementAndGet();
                    } catch (RuntimeException ex) {
                        failure.incrementAndGet();
                    }
                }, executor));
            }
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        }

        InventoryResponse after = inventoryService.getInventory(request.productCode());
        int reservedDelta = beforeAvailable - after.availableStock();

        return new DemoResult(
                request.concurrentRequests(),
                success.get(),
                failure.get(),
                beforeAvailable,
                after.availableStock(),
                after.reservedStock(),
                reservedDelta
        );
    }

    public record DemoResult(
            int concurrentRequests,
            int successCount,
            int failureCount,
            int availableBefore,
            int availableAfter,
            int reservedAfter,
            int availableReduced
    ) {
    }
}

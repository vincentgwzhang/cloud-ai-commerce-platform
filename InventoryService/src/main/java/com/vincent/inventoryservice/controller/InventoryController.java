package com.vincent.inventoryservice.controller;

import com.vincent.inventoryservice.dto.ApiResponse;
import com.vincent.inventoryservice.dto.ConcurrentDemoRequest;
import com.vincent.inventoryservice.dto.HealthResponse;
import com.vincent.inventoryservice.dto.InventoryMutationRequest;
import com.vincent.inventoryservice.dto.InventoryResponse;
import com.vincent.inventoryservice.service.InventoryConcurrencyDemoService;
import com.vincent.inventoryservice.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;
    private final InventoryConcurrencyDemoService concurrencyDemoService;

    public InventoryController(
            InventoryService inventoryService,
            InventoryConcurrencyDemoService concurrencyDemoService
    ) {
        this.inventoryService = inventoryService;
        this.concurrencyDemoService = concurrencyDemoService;
    }

    @GetMapping("/health")
    public ApiResponse<HealthResponse> health() {
        return ApiResponse.ok(new HealthResponse("UP"));
    }

    @GetMapping("/{productCode}")
    public ApiResponse<InventoryResponse> get(@PathVariable String productCode) {
        return ApiResponse.ok(inventoryService.getInventory(productCode));
    }

    @PostMapping("/reserve")
    public ApiResponse<InventoryResponse> reserve(@Valid @RequestBody InventoryMutationRequest request) {
        return ApiResponse.ok(
                "reserved",
                inventoryService.reserve(request.productCode(), request.quantity(), request.requestId())
        );
    }

    @PostMapping("/release")
    public ApiResponse<InventoryResponse> release(@Valid @RequestBody InventoryMutationRequest request) {
        return ApiResponse.ok(
                "released",
                inventoryService.release(request.productCode(), request.quantity(), request.requestId())
        );
    }

    @PostMapping("/deduct")
    public ApiResponse<InventoryResponse> deduct(@Valid @RequestBody InventoryMutationRequest request) {
        return ApiResponse.ok(
                "deducted",
                inventoryService.deduct(request.productCode(), request.quantity(), request.requestId())
        );
    }

    @PostMapping("/demo/concurrent-reserve")
    public ApiResponse<InventoryConcurrencyDemoService.DemoResult> concurrentDemo(
            @Valid @RequestBody ConcurrentDemoRequest request
    ) {
        return ApiResponse.ok(concurrencyDemoService.runConcurrentReserve(request));
    }
}

package com.vincent.inventoryservice.service;

import com.vincent.inventoryservice.cache.InventoryCacheConsistency;
import com.vincent.inventoryservice.cache.InventoryIdempotencyStore;
import com.vincent.inventoryservice.cache.InventoryQueryCache;
import com.vincent.inventoryservice.cache.InventoryRedisCache;
import com.vincent.inventoryservice.cache.LocalHotInventoryCache;
import com.vincent.inventoryservice.config.InventoryProperties;
import com.vincent.inventoryservice.dto.InventoryResponse;
import com.vincent.inventoryservice.entity.Inventory;
import com.vincent.inventoryservice.exception.InsufficientInventoryException;
import com.vincent.inventoryservice.exception.InventoryNotFoundException;
import com.vincent.inventoryservice.lock.InventoryDistributedLock;
import com.vincent.inventoryservice.repository.InventoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core inventory flows. HTTP and Kafka paths both call {@link #reserve} for the same business rules.
 */
@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final InventoryRepository inventoryRepository;
    private final InventoryRedisCache inventoryRedisCache;
    private final InventoryIdempotencyStore idempotencyStore;
    private final InventoryDistributedLock distributedLock;
    private final InventoryQueryCache inventoryQueryCache;
    private final InventoryCacheConsistency cacheConsistency;
    private final LocalHotInventoryCache localHotInventoryCache;
    private final InventoryProperties inventoryProperties;
    private final InventoryMetrics metrics;

    public InventoryService(
            InventoryRepository inventoryRepository,
            InventoryRedisCache inventoryRedisCache,
            InventoryIdempotencyStore idempotencyStore,
            InventoryDistributedLock distributedLock,
            InventoryQueryCache inventoryQueryCache,
            InventoryCacheConsistency cacheConsistency,
            LocalHotInventoryCache localHotInventoryCache,
            InventoryProperties inventoryProperties,
            InventoryMetrics metrics
    ) {
        this.inventoryRepository = inventoryRepository;
        this.inventoryRedisCache = inventoryRedisCache;
        this.idempotencyStore = idempotencyStore;
        this.distributedLock = distributedLock;
        this.inventoryQueryCache = inventoryQueryCache;
        this.cacheConsistency = cacheConsistency;
        this.localHotInventoryCache = localHotInventoryCache;
        this.inventoryProperties = inventoryProperties;
        this.metrics = metrics;
    }

    @Transactional(readOnly = true)
    public InventoryResponse getInventory(String productCode) {
        return inventoryQueryCache.get(productCode, () -> {
            Inventory inventory = loadInventory(productCode);
            warmCacheIfNeeded(inventory);
            return InventoryResponse.from(inventory);
        });
    }

    public void warmCache(Inventory inventory) {
        warmCacheIfNeeded(inventory);
        if (isHotSku(inventory.getProductCode())) {
            localHotInventoryCache.put(inventory.getProductCode(), InventoryResponse.from(inventory));
        }
    }

    /**
     * Reserve stock: Redis atomic pre-check, DB optimistic update, write-through cache refresh.
     * Idempotent on {@code requestId} (HTTP header or Kafka-derived key).
     */
    @Transactional
    public InventoryResponse reserve(String productCode, int quantity, String requestId) {
        return idempotencyStore.findPreviousResult(requestId)
                .orElseGet(() -> executeReserve(productCode, quantity, requestId));
    }

    @Transactional
    public InventoryResponse release(String productCode, int quantity, String requestId) {
        return idempotencyStore.findPreviousResult(requestId)
                .orElseGet(() -> executeRelease(productCode, quantity, requestId));
    }

    @Transactional
    public InventoryResponse deduct(String productCode, int quantity, String requestId) {
        return idempotencyStore.findPreviousResult(requestId)
                .orElseGet(() -> executeDeduct(productCode, quantity, requestId));
    }

    private InventoryResponse executeReserve(String productCode, int quantity, String requestId) {
        if (!idempotencyStore.tryClaim(requestId)) {
            return idempotencyStore.findPreviousResult(requestId)
                    .orElseThrow(() -> new IllegalStateException("Duplicate request in progress: " + requestId));
        }

        String lockToken = distributedLock.tryLock(productCode);
        if (lockToken == null) {
            idempotencyStore.releaseClaim(requestId);
            metrics.recordReservationFailure();
            throw new InsufficientInventoryException(productCode, quantity, 0);
        }

        try {
            Inventory inventory = loadInventoryForUpdate(productCode);
            warmCacheIfNeeded(inventory);

            // Redis-first decrement — fewer pessimistic DB reads under burst traffic.
            // Batch/async DB flush may be added later to reduce write amplification at very high QPS.
            var cacheResult = inventoryRedisCache.tryAtomicDecrement(productCode, quantity);
            if (cacheResult.isEmpty()) {
                inventoryRedisCache.putAvailable(productCode, inventory.getAvailableStock());
                cacheResult = inventoryRedisCache.tryAtomicDecrement(productCode, quantity);
            }
            if (cacheResult.isPresent() && cacheResult.get() == -1) {
                metrics.recordReservationFailure();
                throw new InsufficientInventoryException(productCode, quantity, inventory.getAvailableStock());
            }

            if (inventory.getAvailableStock() < quantity) {
                metrics.recordReservationFailure();
                throw new InsufficientInventoryException(productCode, quantity, inventory.getAvailableStock());
            }

            inventory.setAvailableStock(inventory.getAvailableStock() - quantity);
            inventory.setReservedStock(inventory.getReservedStock() + quantity);
            Inventory saved = inventoryRepository.save(inventory);

            writeThroughCache(saved);
            InventoryResponse response = InventoryResponse.from(saved);
            idempotencyStore.saveResult(requestId, response);
            metrics.recordReservationSuccess();
            log.debug("Reserved {} units of {}", quantity, productCode);
            return response;
        } catch (ObjectOptimisticLockingFailureException ex) {
            inventoryRedisCache.evict(productCode);
            idempotencyStore.releaseClaim(requestId);
            metrics.recordReservationFailure();
            throw new InsufficientInventoryException(productCode, quantity, 0);
        } finally {
            distributedLock.release(productCode, lockToken);
        }
    }

    private InventoryResponse executeRelease(String productCode, int quantity, String requestId) {
        if (!idempotencyStore.tryClaim(requestId)) {
            return idempotencyStore.findPreviousResult(requestId)
                    .orElseThrow(() -> new IllegalStateException("Duplicate request in progress: " + requestId));
        }

        String lockToken = distributedLock.tryLock(productCode);
        if (lockToken == null) {
            idempotencyStore.releaseClaim(requestId);
            throw new InsufficientInventoryException(productCode, quantity, 0);
        }

        try {
            Inventory inventory = loadInventoryForUpdate(productCode);
            if (inventory.getReservedStock() < quantity) {
                throw new InsufficientInventoryException(productCode, quantity, inventory.getReservedStock());
            }
            inventory.setReservedStock(inventory.getReservedStock() - quantity);
            inventory.setAvailableStock(inventory.getAvailableStock() + quantity);
            Inventory saved = inventoryRepository.save(inventory);
            writeThroughCache(saved);
            InventoryResponse response = InventoryResponse.from(saved);
            idempotencyStore.saveResult(requestId, response);
            return response;
        } finally {
            distributedLock.release(productCode, lockToken);
        }
    }

    private InventoryResponse executeDeduct(String productCode, int quantity, String requestId) {
        if (!idempotencyStore.tryClaim(requestId)) {
            return idempotencyStore.findPreviousResult(requestId)
                    .orElseThrow(() -> new IllegalStateException("Duplicate request in progress: " + requestId));
        }

        String lockToken = distributedLock.tryLock(productCode);
        if (lockToken == null) {
            idempotencyStore.releaseClaim(requestId);
            throw new InsufficientInventoryException(productCode, quantity, 0);
        }

        try {
            Inventory inventory = loadInventoryForUpdate(productCode);
            if (inventory.getReservedStock() < quantity) {
                throw new InsufficientInventoryException(productCode, quantity, inventory.getReservedStock());
            }
            inventory.setReservedStock(inventory.getReservedStock() - quantity);
            Inventory saved = inventoryRepository.save(inventory);
            writeThroughCache(saved);
            InventoryResponse response = InventoryResponse.from(saved);
            idempotencyStore.saveResult(requestId, response);
            //PENDING ITEM: future Kafka event — inventory.deducted
            return response;
        } finally {
            distributedLock.release(productCode, lockToken);
        }
    }

    private Inventory loadInventory(String productCode) {
        return inventoryRepository.findByProductCode(productCode)
                .orElseThrow(() -> new InventoryNotFoundException(productCode));
    }

    private Inventory loadInventoryForUpdate(String productCode) {
        return inventoryRepository.findByProductCodeForUpdate(productCode)
                .orElseThrow(() -> new InventoryNotFoundException(productCode));
    }

    private void warmCacheIfNeeded(Inventory inventory) {
        inventoryRedisCache.getAvailable(inventory.getProductCode())
                .ifPresentOrElse(
                        ignored -> { },
                        () -> inventoryRedisCache.putAvailable(inventory.getProductCode(), inventory.getAvailableStock())
                );
    }

    /** Write-through stock counter + invalidate query view (delayed second delete). */
    private void writeThroughCache(Inventory inventory) {
        inventoryRedisCache.putAvailable(inventory.getProductCode(), inventory.getAvailableStock());
        if (isHotSku(inventory.getProductCode())) {
            localHotInventoryCache.put(inventory.getProductCode(), InventoryResponse.from(inventory));
        }
        cacheConsistency.invalidateQueryView(inventory.getProductCode());
    }

    private boolean isHotSku(String productCode) {
        var hot = inventoryProperties.hotProductCodes();
        return hot != null && hot.contains(productCode);
    }
}

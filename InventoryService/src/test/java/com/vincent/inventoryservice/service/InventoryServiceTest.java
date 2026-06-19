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
import com.vincent.inventoryservice.mapper.InventoryMapper;
import com.vincent.inventoryservice.repository.InventoryRepository;
import org.mapstruct.factory.Mappers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private InventoryRedisCache inventoryRedisCache;
    @Mock
    private InventoryIdempotencyStore idempotencyStore;
    @Mock
    private InventoryDistributedLock distributedLock;
    @Mock
    private InventoryQueryCache inventoryQueryCache;
    @Mock
    private InventoryCacheConsistency cacheConsistency;
    @Mock
    private LocalHotInventoryCache localHotInventoryCache;
    @Mock
    private InventoryProperties inventoryProperties;
    @Mock
    private InventoryMetrics metrics;

    @Spy
    private InventoryMapper inventoryMapper = Mappers.getMapper(InventoryMapper.class);

    @InjectMocks
    private InventoryService inventoryService;

    @Test
    void getInventoryReturnsData() {
        var cached = new InventoryResponse("IPHONE17", 10, 2, 1L);
        when(inventoryQueryCache.get(eq("IPHONE17"), any())).thenReturn(cached);

        var response = inventoryService.getInventory("IPHONE17");

        assertThat(response.availableStock()).isEqualTo(10);
        assertThat(response.reservedStock()).isEqualTo(2);
    }

    @Test
    void getInventoryThrowsWhenMissing() {
        when(inventoryQueryCache.get(eq("UNKNOWN"), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<InventoryResponse> loader = invocation.getArgument(1);
            return loader.get();
        });
        when(inventoryRepository.findByProductCode("UNKNOWN")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> inventoryService.getInventory("UNKNOWN"))
                .isInstanceOf(InventoryNotFoundException.class);
    }

    @Test
    void reserveReturnsCachedIdempotentResult() {
        var cached = new InventoryResponse("IPHONE17", 9, 1, 1L);
        when(idempotencyStore.findPreviousResult("req-1")).thenReturn(Optional.of(cached));

        assertThat(inventoryService.reserve("IPHONE17", 1, "req-1")).isEqualTo(cached);
        verify(distributedLock, never()).tryLock(any());
    }

    @Test
    void reserveSucceeds() {
        when(idempotencyStore.findPreviousResult("req-ok")).thenReturn(Optional.empty());
        when(idempotencyStore.tryClaim("req-ok")).thenReturn(true);
        when(distributedLock.tryLock("IPHONE17")).thenReturn("lock-token");

        Inventory inventory = sample("IPHONE17", 10, 0);
        when(inventoryRepository.findByProductCodeForUpdate("IPHONE17")).thenReturn(Optional.of(inventory));
        when(inventoryRedisCache.getAvailable("IPHONE17")).thenReturn(Optional.empty());
        when(inventoryRedisCache.tryAtomicDecrement(eq("IPHONE17"), eq(2))).thenReturn(Optional.of(8));
        when(inventoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InventoryResponse response = inventoryService.reserve("IPHONE17", 2, "req-ok");

        assertThat(response.availableStock()).isEqualTo(8);
        assertThat(response.reservedStock()).isEqualTo(2);
        verify(metrics).recordReservationSuccess();
        verify(distributedLock).release("IPHONE17", "lock-token");
    }

    @Test
    void reserveFailsWhenInsufficient() {
        when(idempotencyStore.findPreviousResult("req-2")).thenReturn(Optional.empty());
        when(idempotencyStore.tryClaim("req-2")).thenReturn(true);
        when(distributedLock.tryLock("IPHONE17")).thenReturn("lock-token");

        Inventory inventory = sample("IPHONE17", 0, 0);
        when(inventoryRepository.findByProductCodeForUpdate("IPHONE17")).thenReturn(Optional.of(inventory));
        when(inventoryRedisCache.getAvailable("IPHONE17")).thenReturn(Optional.of(0));
        when(inventoryRedisCache.tryAtomicDecrement(eq("IPHONE17"), anyInt())).thenReturn(Optional.of(-1));

        assertThatThrownBy(() -> inventoryService.reserve("IPHONE17", 1, "req-2"))
                .isInstanceOf(InsufficientInventoryException.class);

        verify(metrics).recordReservationFailure();
    }

    @Test
    void reserveFailsWhenLockNotAcquired() {
        when(idempotencyStore.findPreviousResult("req-lock")).thenReturn(Optional.empty());
        when(idempotencyStore.tryClaim("req-lock")).thenReturn(true);
        when(distributedLock.tryLock("RTX5090")).thenReturn(null);

        assertThatThrownBy(() -> inventoryService.reserve("RTX5090", 1, "req-lock"))
                .isInstanceOf(InsufficientInventoryException.class);
        verify(idempotencyStore).releaseClaim("req-lock");
    }

    @Test
    void deductSucceeds() {
        when(idempotencyStore.findPreviousResult("req-ded")).thenReturn(Optional.empty());
        when(idempotencyStore.tryClaim("req-ded")).thenReturn(true);
        when(distributedLock.tryLock("RTX5090")).thenReturn("lock-token");

        Inventory inventory = sample("RTX5090", 10, 5);
        when(inventoryRepository.findByProductCodeForUpdate("RTX5090")).thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InventoryResponse response = inventoryService.deduct("RTX5090", 3, "req-ded");

        assertThat(response.reservedStock()).isEqualTo(2);
    }

    @Test
    void reserveRetriesCacheWarmWhenAtomicDecrementMisses() {
        when(idempotencyStore.findPreviousResult("req-warm")).thenReturn(Optional.empty());
        when(idempotencyStore.tryClaim("req-warm")).thenReturn(true);
        when(distributedLock.tryLock("IPHONE17")).thenReturn("lock-token");

        Inventory inventory = sample("IPHONE17", 5, 0);
        when(inventoryRepository.findByProductCodeForUpdate("IPHONE17")).thenReturn(Optional.of(inventory));
        when(inventoryRedisCache.getAvailable("IPHONE17")).thenReturn(Optional.empty());
        when(inventoryRedisCache.tryAtomicDecrement(eq("IPHONE17"), eq(1)))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(4));
        when(inventoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InventoryResponse response = inventoryService.reserve("IPHONE17", 1, "req-warm");

        assertThat(response.availableStock()).isEqualTo(4);
        verify(inventoryRedisCache, org.mockito.Mockito.atLeastOnce()).putAvailable("IPHONE17", 5);
    }

    @Test
    void reserveMapsOptimisticLockFailureToInsufficient() {
        when(idempotencyStore.findPreviousResult("req-opt")).thenReturn(Optional.empty());
        when(idempotencyStore.tryClaim("req-opt")).thenReturn(true);
        when(distributedLock.tryLock("IPHONE17")).thenReturn("lock-token");

        Inventory inventory = sample("IPHONE17", 5, 0);
        when(inventoryRepository.findByProductCodeForUpdate("IPHONE17")).thenReturn(Optional.of(inventory));
        when(inventoryRedisCache.getAvailable("IPHONE17")).thenReturn(Optional.of(5));
        when(inventoryRedisCache.tryAtomicDecrement(eq("IPHONE17"), eq(1))).thenReturn(Optional.of(4));
        when(inventoryRepository.save(any())).thenThrow(new ObjectOptimisticLockingFailureException(Inventory.class, 1L));

        assertThatThrownBy(() -> inventoryService.reserve("IPHONE17", 1, "req-opt"))
                .isInstanceOf(InsufficientInventoryException.class);
        verify(inventoryRedisCache).evict("IPHONE17");
    }

    @Test
    void releaseFailsWhenNotEnoughReserved() {
        when(idempotencyStore.findPreviousResult("req-bad-rel")).thenReturn(Optional.empty());
        when(idempotencyStore.tryClaim("req-bad-rel")).thenReturn(true);
        when(distributedLock.tryLock("PS6")).thenReturn("lock-token");
        when(inventoryRepository.findByProductCodeForUpdate("PS6")).thenReturn(Optional.of(sample("PS6", 50, 1)));

        assertThatThrownBy(() -> inventoryService.release("PS6", 5, "req-bad-rel"))
                .isInstanceOf(InsufficientInventoryException.class);
    }

    @Test
    void releaseSucceeds() {
        when(idempotencyStore.findPreviousResult("req-rel")).thenReturn(Optional.empty());
        when(idempotencyStore.tryClaim("req-rel")).thenReturn(true);
        when(distributedLock.tryLock("PS6")).thenReturn("lock-token");

        Inventory inventory = sample("PS6", 40, 10);
        when(inventoryRepository.findByProductCodeForUpdate("PS6")).thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InventoryResponse response = inventoryService.release("PS6", 5, "req-rel");

        assertThat(response.availableStock()).isEqualTo(45);
        assertThat(response.reservedStock()).isEqualTo(5);
    }

    private static Inventory sample(String code, int available, int reserved) {
        Inventory inventory = new Inventory();
        inventory.setId(1L);
        inventory.setProductCode(code);
        inventory.setAvailableStock(available);
        inventory.setReservedStock(reserved);
        inventory.setVersion(0L);
        inventory.setCreatedAt(Instant.now());
        inventory.setUpdatedAt(Instant.now());
        return inventory;
    }
}

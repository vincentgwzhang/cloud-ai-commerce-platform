package com.vincent.productservice.cache;

import tools.jackson.databind.json.JsonMapper;
import com.vincent.productservice.config.ProductCacheProperties;
import com.vincent.productservice.dto.ProductResponse;
import com.vincent.productservice.entity.Product;
import com.vincent.productservice.entity.ProductStatus;
import com.vincent.productservice.exception.ProductNotFoundException;
import com.vincent.productservice.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductCacheServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductCacheLock cacheLock;
    @Mock
    private LocalHotProductCache localHotCache;
    @Mock
    private ProductCacheMetrics cacheMetrics;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RLock redissonLock;

    private ProductCacheService productCacheService;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(localHotCache.get(any())).thenReturn(Optional.empty());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ProductCacheProperties properties = new ProductCacheProperties(
                Duration.ofMinutes(10),
                Duration.ofMinutes(30),
                Duration.ofMinutes(2),
                Duration.ofSeconds(30),
                Duration.ofSeconds(30),
                0,
                List.of(1L, 2L, 3L)
        );
        productCacheService = new ProductCacheService(
                redisTemplate,
                JsonMapper.builder().findAndAddModules().build(),
                productRepository,
                cacheLock,
                localHotCache,
                cacheMetrics,
                properties,
                redissonClient
        );
    }

    @Test
    void getByIdReturnsCachedValueOnHit() throws Exception {
        ProductResponse response = sampleResponse();
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        when(valueOperations.get("product:detail:1")).thenReturn(mapper.writeValueAsString(response));

        ProductResponse result = productCacheService.getById(1L);

        assertThat(result.name()).isEqualTo("Product 1");
        verify(productRepository, never()).findById(any());
        verify(cacheMetrics).recordHit();
    }

    @Test
    void getByIdLoadsDatabaseOnMissWithLock() throws Exception {
        when(valueOperations.get("product:detail:99")).thenReturn(null);
        when(valueOperations.get("product:notfound:99")).thenReturn(null);
        when(cacheLock.tryAcquire("product:detail:99:lock")).thenReturn("token-1");
        when(productRepository.findById(99L)).thenReturn(Optional.of(sampleEntity(99L)));

        ProductResponse result = productCacheService.getById(99L);

        assertThat(result.id()).isEqualTo(99L);
        verify(valueOperations).set(eq("product:detail:99"), anyString(), any(Duration.class));
        verify(cacheLock).release("product:detail:99:lock", "token-1");
    }

    @Test
    void getByIdThrowsWhenProductMissingAndCachesNullMarker() {
        when(valueOperations.get("product:detail:404")).thenReturn(null);
        when(valueOperations.get("product:notfound:404")).thenReturn(null);
        when(cacheLock.tryAcquire("product:detail:404:lock")).thenReturn("token-1");
        when(productRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productCacheService.getById(404L))
                .isInstanceOf(ProductNotFoundException.class);
        verify(valueOperations).set(eq("product:notfound:404"), eq("1"), any(Duration.class));
    }

    @Test
    void getByIdUsesLocalHotCache() {
        ProductResponse response = sampleResponse();
        when(localHotCache.get(1L)).thenReturn(Optional.of(response));

        ProductResponse result = productCacheService.getById(1L);

        assertThat(result.id()).isEqualTo(1L);
        verify(cacheMetrics).recordHit();
        verify(valueOperations, never()).get(anyString());
    }

    @Test
    void putAndGetHotList() throws Exception {
        ProductResponse response = sampleResponse();
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        productCacheService.put(response);
        productCacheService.putHotList(List.of(response));
        when(valueOperations.get("product:hot:list")).thenReturn(mapper.writeValueAsString(List.of(response)));

        assertThat(productCacheService.getHotList()).isPresent();
    }

    @Test
    void getByIdReturnsFromNullCacheWithoutDb() {
        when(valueOperations.get("product:detail:404")).thenReturn(null);
        when(valueOperations.get("product:notfound:404")).thenReturn("1");

        assertThatThrownBy(() -> productCacheService.getById(404L))
                .isInstanceOf(ProductNotFoundException.class);
        verify(productRepository, never()).findById(any());
    }

    @Test
    void getByIdFallsBackToDatabaseAndLocalCacheWhenRedisLockUnavailable() {
        ProductResponse localResponse = sampleResponse();
        when(localHotCache.get(1L)).thenReturn(Optional.empty()).thenReturn(Optional.of(localResponse));
        when(valueOperations.get("product:detail:1")).thenReturn(null);
        when(valueOperations.get("product:notfound:1")).thenReturn(null);
        when(cacheLock.tryAcquire("product:detail:1:lock")).thenThrow(new RuntimeException("redis down"));
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleEntity(1L)));

        ProductResponse first = productCacheService.getById(1L);
        ProductResponse second = productCacheService.getById(1L);

        assertThat(first.id()).isEqualTo(1L);
        assertThat(second.id()).isEqualTo(1L);
        verify(localHotCache).put(eq(1L), any(ProductResponse.class));
        verify(productRepository, times(1)).findById(1L);
        verify(valueOperations, never()).set(eq("product:detail:1"), anyString(), any(Duration.class));
    }

    @Test
    void getByIdWaitFallbackWarmsLocalCacheWhenPeerDoesNotFillRedis() {
        ProductResponse localResponse = ProductResponse.from(sampleEntity(2L));
        when(localHotCache.get(2L)).thenReturn(Optional.empty()).thenReturn(Optional.of(localResponse));
        when(valueOperations.get("product:detail:2")).thenReturn(null);
        when(valueOperations.get("product:notfound:2")).thenReturn(null);
        when(cacheLock.tryAcquire("product:detail:2:lock")).thenReturn(null);
        when(productRepository.findById(2L)).thenReturn(Optional.of(sampleEntity(2L)));

        ProductResponse first = productCacheService.getById(2L);
        ProductResponse second = productCacheService.getById(2L);

        assertThat(first.id()).isEqualTo(2L);
        assertThat(second.id()).isEqualTo(2L);
        verify(localHotCache).put(eq(2L), any(ProductResponse.class));
        verify(productRepository, times(1)).findById(2L);
    }

    @Test
    void getByIdWithRedissonReturnsCachedValueAndWarmsLocalHotCache() throws Exception {
        ProductResponse response = sampleResponse();
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        when(valueOperations.get("product:detail:1")).thenReturn(mapper.writeValueAsString(response));

        ProductResponse result = productCacheService.getByIdWithRedisson(1L);

        assertThat(result.name()).isEqualTo("Product 1");
        verify(productRepository, never()).findById(any());
        verify(cacheMetrics).recordHit();
        verify(localHotCache).put(eq(1L), any(ProductResponse.class));
        verify(redissonClient, never()).getLock(anyString());
    }

    @Test
    void getByIdWithRedissonLoadsDatabaseOnMissWithLock() throws Exception {
        when(valueOperations.get("product:detail:99")).thenReturn(null);
        when(valueOperations.get("product:notfound:99")).thenReturn(null);
        when(redissonClient.getLock("product:detail:99:lock")).thenReturn(redissonLock);
        when(redissonLock.tryLock(0, Duration.ofSeconds(30).toMillis(), TimeUnit.MILLISECONDS)).thenReturn(true);
        when(redissonLock.isHeldByCurrentThread()).thenReturn(true);
        when(productRepository.findById(99L)).thenReturn(Optional.of(sampleEntity(99L)));

        ProductResponse result = productCacheService.getByIdWithRedisson(99L);

        assertThat(result.id()).isEqualTo(99L);
        verify(valueOperations).set(eq("product:detail:99"), anyString(), any(Duration.class));
        verify(redissonLock).unlock();
    }

    @Test
    void getByIdWithRedissonFallsBackToDatabaseAndLocalCacheWhenLockUnavailable() throws Exception {
        ProductResponse localResponse = sampleResponse();
        when(localHotCache.get(1L)).thenReturn(Optional.empty()).thenReturn(Optional.of(localResponse));
        when(valueOperations.get("product:detail:1")).thenReturn(null);
        when(valueOperations.get("product:notfound:1")).thenReturn(null);
        when(redissonClient.getLock("product:detail:1:lock")).thenReturn(redissonLock);
        when(redissonLock.tryLock(0, Duration.ofSeconds(30).toMillis(), TimeUnit.MILLISECONDS))
                .thenThrow(new RuntimeException("redis down"));
        when(productRepository.findById(1L)).thenReturn(Optional.of(sampleEntity(1L)));

        ProductResponse first = productCacheService.getByIdWithRedisson(1L);
        ProductResponse second = productCacheService.getByIdWithRedisson(1L);

        assertThat(first.id()).isEqualTo(1L);
        assertThat(second.id()).isEqualTo(1L);
        verify(localHotCache).put(eq(1L), any(ProductResponse.class));
        verify(productRepository, times(1)).findById(1L);
        verify(redissonLock, never()).unlock();
        verify(valueOperations, never()).set(eq("product:detail:1"), anyString(), any(Duration.class));
    }

    @Test
    void getByIdWithRedissonWaitsForPeerWhenLockIsBusy() throws Exception {
        ProductResponse response = ProductResponse.from(sampleEntity(2L));
        JsonMapper mapper = JsonMapper.builder().findAndAddModules().build();
        when(valueOperations.get("product:detail:2"))
                .thenReturn(null)
                .thenReturn(mapper.writeValueAsString(response));
        when(valueOperations.get("product:notfound:2")).thenReturn(null);
        when(redissonClient.getLock("product:detail:2:lock")).thenReturn(redissonLock);
        when(redissonLock.tryLock(0, Duration.ofSeconds(30).toMillis(), TimeUnit.MILLISECONDS)).thenReturn(false);

        ProductResponse result = productCacheService.getByIdWithRedisson(2L);

        assertThat(result.id()).isEqualTo(2L);
        verify(productRepository, never()).findById(any());
        verify(redissonLock, never()).unlock();
    }

    private static Product sampleEntity(Long id) {
        Product product = new Product();
        product.setId(id);
        product.setProductCode("CODE-" + id);
        product.setName("Product " + id);
        product.setDescription("desc");
        product.setPrice(BigDecimal.TEN);
        product.setStock(5);
        product.setStatus(ProductStatus.ACTIVE);
        product.setCreatedAt(Instant.now());
        product.setUpdatedAt(Instant.now());
        return product;
    }

    private static ProductResponse sampleResponse() {
        return ProductResponse.from(sampleEntity(1L));
    }
}

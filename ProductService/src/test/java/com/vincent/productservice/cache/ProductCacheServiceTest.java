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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
                properties
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
